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

import java.net.SocketTimeoutException;
import java.util.Map;

// RiskWatch 위험 평가 호출 (재시도, 실패 시 감사 기록 + fallback)
@Component
@Slf4j
public class RiskWatchClient {

    // RiskWatch에 알리는 호출 시스템 이름, 연동 실패 감사 기록의 행위자
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

    // RiskWatch 전용 RestTemplate, 서킷 브레이커 주입
    public RiskWatchClient(@Qualifier("riskWatchRestTemplate") RestTemplate restTemplate,
            AuditService auditService, CircuitBreaker riskWatchCircuitBreaker) {
        this.restTemplate = restTemplate;
        this.auditService = auditService;
        this.circuitBreaker = riskWatchCircuitBreaker;
        // 서킷 상태 변경(CLOSED_TO_OPEN 등)을 감사 기록에 한 번씩 남김
        this.circuitBreaker.getEventPublisher().onStateTransition(this::recordStateTransition);
    }

    // 위험 평가 요청 (항상 결과를 돌려줌: 성공이면 available=true, 실패면 fallback)
    public RiskEvaluationResponse evaluateRisk(RiskEvaluationRequest request) {
        String url = riskWatchBaseUrl + "/api/v1/risk/evaluate";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (request.getTraceId() != null)
            headers.set("X-Trace-Id", request.getTraceId());
        headers.set("X-Event-Version", request.getEventVersion());
        headers.set("X-Source-System", SOURCE_SYSTEM);

        HttpEntity<RiskEvaluationRequest> entity = new HttpEntity<>(request, headers);
        String reason = "unknown";
        boolean shortCircuited = false;
        int maxAttempts = riskWatchRetryCount + 1;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                log.info("[{}] RiskWatch evaluate attempt {}/{} userId={}",
                        request.getTraceId(), attempt, maxAttempts, request.getUserId());

                // 서킷 브레이커를 거쳐 요청 전송 (차단 중이면 보내지 않음)
                @SuppressWarnings("unchecked")
                ResponseEntity<Map<String, Object>> response = circuitBreaker.executeSupplier(
                        () -> restTemplate.postForEntity(url, entity,
                                (Class<Map<String, Object>>) (Class<?>) Map.class));

                RiskEvaluationResponse result = toResult(response.getBody());
                if (result != null) {
                    log.info("[{}] RiskWatch response: level={} score={}",
                            request.getTraceId(), result.getRiskLevel(), result.getRiskScore());
                    return result;
                }
                // SUCCESS가 아닌 응답은 재시도하지 않음
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

    // RiskWatch 성공 응답을 결과로 변환 (SUCCESS가 아니거나 data가 없으면 null)
    private RiskEvaluationResponse toResult(Map<String, Object> raw) {
        if (raw == null || !"SUCCESS".equals(raw.get("status"))) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) raw.get("data");
        if (data == null) {
            return null;
        }
        Object scoreObj = data.get("riskScore");
        return RiskEvaluationResponse.builder()
                .riskLevel(data.get("riskLevel") == null ? "UNKNOWN" : String.valueOf(data.get("riskLevel")))
                .riskScore(scoreObj instanceof Number n ? n.intValue() : null)
                .recommendation(String.valueOf(data.getOrDefault("reason", "")))
                .traceId(String.valueOf(data.getOrDefault("traceId", "")))
                .available(true)
                .build();
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
                .riskLevel("UNKNOWN")
                .riskScore(0)
                .recommendation("Risk evaluation service temporarily unavailable.")
                .traceId(traceId)
                .available(false)
                .build();
    }
}
