package com.homefinmate.integration;

import com.homefinmate.dto.risk.RiskEvaluationRequest;
import com.homefinmate.dto.risk.RiskEvaluationResponse;
import com.homefinmate.service.AuditService;
import lombok.extern.slf4j.Slf4j;
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

    // 감사 기록 action 최대 길이
    private static final int AUDIT_ACTION_MAX = 255;
    private static final String FAILURE_PREFIX = "RISKWATCH_INTEGRATION_FAILURE: ";

    @Value("${app.riskwatch.base-url:http://localhost:8120}")
    private String riskWatchBaseUrl;

    @Value("${app.riskwatch.retry-count:2}")
    private int riskWatchRetryCount;

    // 재시도 대기 시작값 (밀리초, 시도마다 2배)
    @Value("${app.riskwatch.backoff-ms:200}")
    private long backoffMs;

    private final RestTemplate restTemplate;
    private final AuditService auditService;

    // RiskWatch 전용 RestTemplate 주입
    public RiskWatchClient(@Qualifier("riskWatchRestTemplate") RestTemplate restTemplate,
            AuditService auditService) {
        this.restTemplate = restTemplate;
        this.auditService = auditService;
    }

    // 위험 평가 요청 (항상 결과를 돌려줌: 성공이면 available=true, 실패면 fallback)
    public RiskEvaluationResponse evaluateRisk(RiskEvaluationRequest request) {
        String url = riskWatchBaseUrl + "/api/v1/risk/evaluate";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (request.getTraceId() != null)
            headers.set("X-Trace-Id", request.getTraceId());
        headers.set("X-Event-Version", request.getEventVersion());
        headers.set("X-Source-System", "HOMEFIT_MATE");

        HttpEntity<RiskEvaluationRequest> entity = new HttpEntity<>(request, headers);
        String reason = "unknown";
        int maxAttempts = riskWatchRetryCount + 1;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                log.info("[{}] RiskWatch evaluate attempt {}/{} userId={}",
                        request.getTraceId(), attempt, maxAttempts, request.getUserId());

                @SuppressWarnings("unchecked")
                ResponseEntity<Map<String, Object>> response = restTemplate.postForEntity(url, entity,
                        (Class<Map<String, Object>>) (Class<?>) Map.class);

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
                log.warn("[{}] RiskWatch attempt {} failed: {}", request.getTraceId(), attempt, reason);

                if (!isRetryable(e) || attempt == maxAttempts) {
                    break;
                }
                sleepBackoff(attempt);
            }
        }

        log.warn("[{}] RiskWatch unavailable — returning fallback. reason={}", request.getTraceId(), reason);
        recordFailure(reason, request.getTraceId());
        return fallback(request.getTraceId());
    }

    // 재시도 대상: 5xx, 응답 타임아웃이 아닌 연결 오류
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
                .riskLevel(String.valueOf(data.get("riskLevel")))
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
            auditService.logAudit("SYSTEM", "HOMEFIT_MATE", action, traceId);
        } catch (Exception e) {
            log.error("[{}] Failed to record RiskWatch failure audit: {}", traceId, e.getMessage());
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
