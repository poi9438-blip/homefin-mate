package com.homefinmate.integration;

import com.homefinmate.dto.risk.RiskEvaluationRequest;
import com.homefinmate.dto.risk.RiskEvaluationResponse;
import com.homefinmate.service.AuditService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import lombok.extern.slf4j.Slf4j;
import com.homefinmate.common.TraceIds;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;

// RiskWatch 위험 평가 호출 (재시도, 실패 시 감사 기록 + fallback)
@Component
@Slf4j
public class RiskWatchClient {

    // 연동 실패 감사 기록의 행위자 (RiskWatch에는 보내지 않음: 요청 서버는 토큰으로 정함)
    public static final String SOURCE_SYSTEM = "HOMEFIN_MATE";

    // 감사 기록 action 최대 길이
    private static final int AUDIT_ACTION_MAX = 255;
    private static final String FAILURE_PREFIX = "RISKWATCH_INTEGRATION_FAILURE: ";
    private static final String CIRCUIT_PREFIX = "RISKWATCH_CIRCUIT_";

    @Value("${app.riskwatch.base-url:http://localhost:8120}")
    private String riskWatchBaseUrl;

    @Value("${app.riskwatch.retry-count:2}")
    private int riskWatchRetryCount;

    // 재시도 대기 시작값 (밀리초, 시도마다 2배)
    @Value("${app.riskwatch.backoff-ms:200}")
    private long backoffMs;

    private final RestTemplate restTemplate;
    private final AuditService auditService;
    private final CircuitBreaker circuitBreaker;
    private final RiskWatchTokens tokens;

    // RiskWatch 전용 RestTemplate, 서킷 브레이커, 서비스 토큰 주입
    public RiskWatchClient(@Qualifier("riskWatchRestTemplate") RestTemplate restTemplate,
            AuditService auditService, CircuitBreaker riskWatchCircuitBreaker, RiskWatchTokens tokens) {
        this.restTemplate = restTemplate;
        this.auditService = auditService;
        this.circuitBreaker = riskWatchCircuitBreaker;
        this.tokens = tokens;
        // 서킷 상태 변경(CLOSED_TO_OPEN 등)을 감사 기록에 한 번씩 남김
        this.circuitBreaker.getEventPublisher().onStateTransition(this::recordStateTransition);
    }

    // 위험 평가 요청 (항상 결과를 돌려줌: 성공이면 available=true, 실패면 fallback)
    public RiskEvaluationResponse evaluateRisk(RiskEvaluationRequest request) {
        // RiskWatch 평가 API (서비스 토큰 + X-Trace-Id)
        String url = riskWatchBaseUrl + "/api/evaluations";

        String reason = "unknown";
        boolean shortCircuited = false;
        int maxAttempts = riskWatchRetryCount + 1;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                // 토큰은 시도마다 새로 만듦 (유효 기간 1분이라 재시도 대기 중 만료되지 않게)
                HttpEntity<RiskEvaluationRequest> entity = new HttpEntity<>(request, headers(request.getTraceId()));
                log.info("[{}] RiskWatch evaluate attempt {}/{} applicant={} product={}",
                        request.getTraceId(), attempt, maxAttempts, request.getApplicantRef(),
                        request.getProductCode());

                // 서킷 브레이커를 거쳐 요청 전송 (차단 중이면 보내지 않음)
                @SuppressWarnings("unchecked")
                ResponseEntity<Map<String, Object>> response = circuitBreaker.executeSupplier(
                        () -> restTemplate.postForEntity(url, entity,
                                (Class<Map<String, Object>>) (Class<?>) Map.class));

                RiskEvaluationResponse result = toResult(response.getBody(), request.getTraceId());
                if (result != null) {
                    log.info("[{}] RiskWatch response: evaluationId={} verdict={} criteria={}",
                            request.getTraceId(), result.getEvaluationId(), result.getVerdict(),
                            result.getCriteriaVersion());
                    return result;
                }
                // SUCCESS가 아니거나 판정 칸이 빠진 응답은 재시도하지 않음
                reason = "Empty or non-SUCCESS response from RiskWatch";
                break;

            } catch (Exception e) {
                // 어떤 예외든 기록 후 재시도 여부 판단 (재시도 안 하면 fallback)
                reason = e.getClass().getSimpleName() + ": " + e.getMessage();
                shortCircuited = e instanceof CallNotPermittedException;
                log.warn("[{}] RiskWatch attempt {} failed: {}", request.getTraceId(), attempt, reason);

                if (!isRetryable(e) || attempt == maxAttempts) {
                    break;
                }
                sleepBackoff(attempt);
            }
        }

        log.warn("[{}] RiskWatch unavailable — returning fallback. reason={}", request.getTraceId(), reason);

        // 실제로 호출했다가 실패한 경우만 연동 실패 기록
        if (!shortCircuited) {
            recordFailure(reason, request.getTraceId());
        }
        return fallback(request.getTraceId());
    }

    // 재시도 대상: 5xx, 응답 타임아웃이 아닌 연결 오류\
    private boolean isRetryable(Exception e) {
        if (e instanceof HttpServerErrorException) {
            return true;
        }
        if (e instanceof ResourceAccessException) {
            return !(e.getCause() instanceof SocketTimeoutException);
        }
        return false;
    }

    // 재시도 전 대기 (backoffMs × 2^(attempt-1))
    private void sleepBackoff(int attempt) {
        long wait = backoffMs * (1L << (attempt - 1));
        if (wait <= 0)
            return;
        try {
            Thread.sleep(wait);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    // 요청 헤더: JSON, 서비스 토큰, traceId
    private HttpHeaders headers(String traceId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(tokens.issue());
        if (traceId != null)
            headers.set("X-Trace-Id", traceId);
        return headers;
    }

    // RiskWatch 성공 응답을 결과로 변환 (SUCCESS가 아니거나 판정 칸이 빠졌으면 null)
    @SuppressWarnings("unchecked")
    private RiskEvaluationResponse toResult(Map<String, Object> raw, String traceId) {
        if (raw == null || !"SUCCESS".equals(raw.get("status")) || !(raw.get("data") instanceof Map<?, ?>)) {
            return null;
        }
        Map<String, Object> data = (Map<String, Object>) raw.get("data");
        String evaluationId = text(data, "evaluationId");
        String verdict = text(data, "verdict");
        String criteriaVersion = text(data, "criteriaVersion");
        String riskLevel = text(data, "riskLevel");
        if (evaluationId == null || verdict == null || criteriaVersion == null || riskLevel == null
                || !(data.get("riskScore") instanceof Number score)) {
            return null;
        }
        return RiskEvaluationResponse.builder()
                .status(RiskEvaluationResponse.Status.EVALUATED)
                .evaluationId(evaluationId)
                .productCode(text(data, "productCode"))
                .verdict(verdict)
                .criteriaVersion(criteriaVersion)
                .riskLevel(riskLevel)
                .riskScore(score.intValue())
                .recommendation(String.valueOf(data.getOrDefault("recommendation", "")))
                .reasons(reasons(data.get("reasons")))
                .notEvaluated(data.get("notEvaluated") instanceof List<?> list
                        ? list.stream().map(String::valueOf).toList()
                        : List.of())
                .traceId(traceId)
                .available(true)
                .build();
    }

    private static String text(Map<String, Object> data, String field) {
        return data.get(field) instanceof String s && !s.isBlank() ? s : null;
    }

    // 규칙별 근거 (모양이 다른 항목은 건너뜀)
    @SuppressWarnings("unchecked")
    private static List<RiskEvaluationResponse.Reason> reasons(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(item -> item instanceof Map<?, ?>)
                .map(item -> (Map<String, Object>) item)
                .map(r -> new RiskEvaluationResponse.Reason(text(r, "code"), text(r, "name"),
                        decimal(r.get("threshold")), decimal(r.get("actual")), text(r, "result"),
                        text(r, "detail")))
                .toList();
    }

    private static BigDecimal decimal(Object value) {
        return value instanceof Number n ? new BigDecimal(n.toString()) : null;
    }

    // 연동 실패 감사 기록 (컬럼 길이에 맞게 자름, 기록 실패가 fallback을 막지 않음)
    private void recordFailure(String reason, String traceId) {
        String action = FAILURE_PREFIX + reason;
        if (action.length() > AUDIT_ACTION_MAX) {
            action = action.substring(0, AUDIT_ACTION_MAX);
        }
        try {
            auditService.logAudit("SYSTEM", SOURCE_SYSTEM, action, traceId);
        } catch (Exception e) {
            log.error("[{}] Failed to record RiskWatch failure audit: {}", traceId, e.getMessage());
        }
    }

    // 서킷 상태 변경 감사 기록 (traceId는 상태를 바꾼 요청의 것, 기록 실패는 무시)
    private void recordStateTransition(CircuitBreakerOnStateTransitionEvent event) {
        String action = CIRCUIT_PREFIX + event.getStateTransition().name();
        log.warn("RiskWatch circuit breaker {}", event.getStateTransition().name());
        try {
            auditService.logAudit("SYSTEM", SOURCE_SYSTEM, action, TraceIds.current());
        } catch (Exception e) {
            log.error("Failed to record circuit breaker transition: {}", e.getMessage());
        }
    }

    // 대체 결과 (UNKNOWN, 점수 0, available=false)
    private RiskEvaluationResponse fallback(String traceId) {
        return RiskEvaluationResponse.builder()
                .status(RiskEvaluationResponse.Status.UNAVAILABLE)
                .riskLevel("UNKNOWN")
                .riskScore(0)
                .recommendation("Risk evaluation service temporarily unavailable.")
                .traceId(traceId)
                .available(false)
                .build();
    }
}
