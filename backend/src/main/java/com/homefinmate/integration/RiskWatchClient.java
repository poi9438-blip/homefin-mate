package com.homefinmate.integration;

import com.homefinmate.dto.risk.RiskEvaluationRequest;
import com.homefinmate.dto.risk.RiskEvaluationResponse;
import com.homefinmate.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

// RiskWatch 위험 평가 호출 (재시도, 실패 시 감사 기록 + fallback)
@Component
@RequiredArgsConstructor
@Slf4j
public class RiskWatchClient {

    @Value("${app.riskwatch.base-url:http://localhost:8120}")
    private String riskWatchBaseUrl;

    @Value("${app.riskwatch.retry-count:2}")
    private int riskWatchRetryCount;

    private final RestTemplate restTemplate;
    private final AuditService auditService;

    // 위험 평가 요청 (항상 결과를 돌려줌: 성공이면 available=true, 실패면 fallback)
    public RiskEvaluationResponse evaluateRisk(RiskEvaluationRequest request) {
        // RiskWatch 평가 주소
        String url = riskWatchBaseUrl + "/api/v1/risk/evaluate";

        // 요청 헤더 (JSON, traceId, 이벤트 버전, 보낸 시스템)
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (request.getTraceId() != null)
            headers.set("X-Trace-Id", request.getTraceId());
        headers.set("X-Event-Version", request.getEventVersion());
        headers.set("X-Source-System", "HOMEFIT_MATE");

        HttpEntity<RiskEvaluationRequest> entity = new HttpEntity<>(request, headers);
        Exception lastException = null;

        // 최대 (재시도 횟수 + 1)번 시도
        for (int attempt = 1; attempt <= riskWatchRetryCount + 1; attempt++) {
            try {
                log.info("[{}] RiskWatch evaluate attempt {}/{} userId={}",
                        request.getTraceId(), attempt, riskWatchRetryCount + 1, request.getUserId());

                @SuppressWarnings("unchecked")
                ResponseEntity<Map<String, Object>> response = restTemplate.postForEntity(url, entity,
                        (Class<Map<String, Object>>) (Class<?>) Map.class);

                Map<String, Object> raw = response.getBody();
                if (raw != null && "SUCCESS".equals(raw.get("status"))) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) raw.get("data");
                    if (data != null) {
                        Object scoreObj = data.get("riskScore");
                        RiskEvaluationResponse result = RiskEvaluationResponse.builder()
                                .riskLevel(String.valueOf(data.get("riskLevel")))
                                .riskScore(scoreObj instanceof Number n ? n.intValue() : null)
                                .recommendation(String.valueOf(data.getOrDefault("reason", "")))
                                .traceId(String.valueOf(data.getOrDefault("traceId", "")))
                                .available(true)
                                .build();
                        log.info("[{}] RiskWatch response: level={} score={}",
                                request.getTraceId(), result.getRiskLevel(), result.getRiskScore());
                        return result;
                    }
                }
                // SUCCESS가 아니거나 data가 없으면 재시도
                lastException = new RestClientException("Empty or non-SUCCESS response from RiskWatch");

            } catch (RestClientException e) {
                // 호출 실패: 기록하고 다음 시도로
                lastException = e;
                log.warn("[{}] RiskWatch attempt {} failed: {}",
                        request.getTraceId(), attempt, e.getMessage());
            }
        }

        // 모든 시도 실패: 감사 기록 남기고 fallback 반환
        String reason = lastException != null ? lastException.getMessage() : "unknown";
        log.warn("[{}] RiskWatch unavailable after {} attempts — returning fallback. reason={}",
                request.getTraceId(), riskWatchRetryCount + 1, reason);

        // 연동 실패 감사 기록 (SYSTEM)
        auditService.logAudit(
                "SYSTEM",
                "HOMEFIT_MATE",
                "RISKWATCH_INTEGRATION_FAILURE: " + reason,
                request.getTraceId());

        return fallback(request.getTraceId());
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
