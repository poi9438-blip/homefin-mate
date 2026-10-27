package com.homefinmate.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homefinmate.dto.application.ApplicationDtos.FinFlowView;
import com.homefinmate.dto.application.FinFlowApplicationRequest;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.OffsetDateTime;
import java.util.Map;

// FinFlow 신청 API 호출 (서비스 토큰 + X-Trace-Id). 실패는 FinFlowException 종류로 알려 주고, 화면 응답은 서비스가 정함
@Slf4j
@Component
public class FinFlowClient {

    // 접수 결과 (replayed: 같은 Idempotency-Key의 처음 결과를 FinFlow가 다시 보냄)
    public record Received(String applicationId, String status, OffsetDateTime receivedAt, boolean replayed) {
    }

    // 실패 종류: 응답 없음(연결·시간 초과·5xx) / 없는 신청 / 409(진행 중 신청·잘못된 상태 전이) / 그 밖의 거절(4xx)
    public enum Failure {
        UNAVAILABLE, NOT_FOUND, CONFLICT, REJECTED
    }

    @Getter
    public static class FinFlowException extends RuntimeException {
        private final Failure failure;

        FinFlowException(Failure failure, String message) {
            super(message, null, false, false);
            this.failure = failure;
        }
    }

    private final RestTemplate restTemplate;
    private final FinFlowTokens tokens;
    private final ObjectMapper objectMapper;

    @Value("${app.finflow.base-url:http://localhost:8110}")
    private String baseUrl;

    // 접수 재시도 횟수 (총 시도 = 값 + 1)
    @Value("${app.finflow.retry-count:2}")
    private int retryCount;

    @Value("${app.finflow.backoff-ms:200}")
    private long backoffMs;

    public FinFlowClient(@Qualifier("finFlowRestTemplate") RestTemplate restTemplate, FinFlowTokens tokens,
            ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.tokens = tokens;
        this.objectMapper = objectMapper;
    }

    // 신청 접수 (연결 실패·5xx·시간 초과는 같은 키로 다시 보냄, 4xx는 다시 보내지 않음)
    public Received receive(FinFlowApplicationRequest request, String idempotencyKey, String traceId) {
        String url = baseUrl + "/api/applications";
        String reason = "unknown";
        for (int attempt = 1; attempt <= retryCount + 1; attempt++) {
            try {
                HttpHeaders headers = headers(traceId);
                headers.set("Idempotency-Key", idempotencyKey);
                @SuppressWarnings("unchecked")
                ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.POST,
                        new HttpEntity<>(request, headers), (Class<Map<String, Object>>) (Class<?>) Map.class);
                Map<String, Object> data = data(response.getBody());
                boolean replayed = response.getStatusCode() == HttpStatus.OK
                        || "true".equals(response.getHeaders().getFirst("Idempotent-Replayed"));
                log.info("[{}] FinFlow application received applicationId={} replayed={} attempt={}", traceId,
                        data.get("applicationId"), replayed, attempt);
                return new Received(String.valueOf(data.get("applicationId")), String.valueOf(data.get("status")),
                        objectMapper.convertValue(data.get("receivedAt"), OffsetDateTime.class), replayed);
            } catch (HttpClientErrorException e) {
                throw clientError(e, traceId);
            } catch (HttpServerErrorException | ResourceAccessException e) {
                reason = e.getClass().getSimpleName();
                log.warn("[{}] FinFlow receive attempt {} failed: {}", traceId, attempt, reason);
                sleep(attempt);
            }
        }
        throw new FinFlowException(Failure.UNAVAILABLE, "FinFlow unavailable: " + reason);
    }

    // 신청자 본인 조회 (다른 신청자의 신청이면 FinFlow가 404)
    public FinFlowView find(String applicationId, String applicantRef, String traceId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl).path("/api/applications/{id}")
                .queryParam("applicantRef", applicantRef).buildAndExpand(applicationId).toUriString();
        return call(url, HttpMethod.GET, null, traceId);
    }

    // 신청자 취소 (접수·심사 중만, 이미 취소됐으면 그대로)
    public FinFlowView cancel(String applicationId, String applicantRef, String traceId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl).path("/api/applications/{id}/cancel")
                .buildAndExpand(applicationId).toUriString();
        return call(url, HttpMethod.POST, Map.of("applicantRef", applicantRef), traceId);
    }

    @SuppressWarnings("unchecked")
    private FinFlowView call(String url, HttpMethod method, Object body, String traceId) {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, method,
                    new HttpEntity<>(body, headers(traceId)), (Class<Map<String, Object>>) (Class<?>) Map.class);
            return objectMapper.convertValue(data(response.getBody()), FinFlowView.class);
        } catch (HttpClientErrorException e) {
            throw clientError(e, traceId);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            log.warn("[{}] FinFlow {} failed: {}", traceId, method, e.getClass().getSimpleName());
            throw new FinFlowException(Failure.UNAVAILABLE, "FinFlow unavailable: " + e.getClass().getSimpleName());
        }
    }

    private HttpHeaders headers(String traceId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(tokens.issue());
        if (traceId != null) {
            headers.set("X-Trace-Id", traceId);
        }
        return headers;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(Map<String, Object> body) {
        if (body == null || !(body.get("data") instanceof Map<?, ?> data)) {
            throw new FinFlowException(Failure.UNAVAILABLE, "FinFlow response without data");
        }
        return (Map<String, Object>) data;
    }

    // 4xx: 404 없는 신청, 409 진행 중 신청·잘못된 전이(FinFlow 메시지 그대로), 나머지는 거절 (요청을 고쳐야 함)
    private FinFlowException clientError(HttpClientErrorException e, String traceId) {
        String message = message(e);
        log.warn("[{}] FinFlow rejected {}: {}", traceId, e.getStatusCode().value(), message);
        if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
            return new FinFlowException(Failure.NOT_FOUND, message);
        }
        if (e.getStatusCode() == HttpStatus.CONFLICT) {
            return new FinFlowException(Failure.CONFLICT, message);
        }
        return new FinFlowException(Failure.REJECTED, message);
    }

    // FinFlow 공통 오류 형식의 message (읽을 수 없으면 상태 코드)
    private String message(HttpClientErrorException e) {
        try {
            Object message = objectMapper.readValue(e.getResponseBodyAsString(), Map.class).get("message");
            if (message instanceof String s && !s.isBlank()) {
                return s;
            }
        } catch (Exception ignored) {
            // 본문이 JSON이 아님
        }
        return "HTTP " + e.getStatusCode().value();
    }

    // 재시도 전 대기 (backoffMs × 2^(attempt-1))
    private void sleep(int attempt) {
        long wait = backoffMs * (1L << (attempt - 1));
        if (wait <= 0 || attempt > retryCount) {
            return;
        }
        try {
            Thread.sleep(wait);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
