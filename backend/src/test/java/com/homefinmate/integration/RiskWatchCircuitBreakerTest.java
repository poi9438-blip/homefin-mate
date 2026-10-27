package com.homefinmate.integration;

import com.homefinmate.config.ResilienceConfig;
import com.homefinmate.dto.risk.RiskEvaluationRequest;
import com.homefinmate.dto.risk.RiskEvaluationResponse;
import com.homefinmate.service.AuditService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// RiskWatch 서킷 브레이커 테스트 (차단, 실패 집계 기준, 복구)
@ExtendWith(MockitoExtension.class)
class RiskWatchCircuitBreakerTest {

    private static final String URL = "http://riskwatch.test/api/evaluations";

    @Mock
    AuditService auditService;

    private MockRestServiceServer server;
    private CircuitBreaker circuitBreaker;
    private RiskWatchClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        circuitBreaker = new ResilienceConfig().riskWatchCircuitBreaker(50, 4, 4, 60_000, 1);
        client = RiskWatchClientTest.client(restTemplate, auditService, circuitBreaker, 0);
    }

    private RiskEvaluationRequest request() {
        return RiskWatchClientTest.request("hfm-cb-1");
    }

    @Test
    @DisplayName("[TC-RISK-201] 연결 실패가 4번 이어지면 차단, 5번째는 RiskWatch로 보내지 않고 바로 fallback, 차단 기록은 1번만")
    void consecutiveFailures_openCircuit_andSkipCall() {
        server.expect(ExpectedCount.times(4), requestTo(URL))
                .andRespond(withException(new ConnectException("Connection refused")));

        for (int i = 0; i < 4; i++) {
            assertThat(client.evaluateRisk(request()).isAvailable()).isFalse();
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        RiskEvaluationResponse fifth = client.evaluateRisk(request());

        assertThat(fifth.isAvailable()).isFalse();
        server.verify(); // RiskWatch에는 4번만 나감
        verify(auditService, times(4)).logAudit(eq("SYSTEM"), eq(RiskWatchClient.SOURCE_SYSTEM),
                startsWith("RISKWATCH_INTEGRATION_FAILURE: ResourceAccessException"), eq("hfm-cb-1"));
        verify(auditService, never()).logAudit(any(), any(), contains("CallNotPermittedException"), any());
        verify(auditService, times(1)).logAudit(eq("SYSTEM"), eq(RiskWatchClient.SOURCE_SYSTEM),
                eq("RISKWATCH_CIRCUIT_CLOSED_TO_OPEN"), any());
    }

    @Test
    @DisplayName("[TC-RISK-202] 400·SUCCESS 아닌 200 응답은 실패로 세지 않아 차단되지 않음")
    void clientErrorsAndNonSuccess_doNotOpenCircuit() {
        server.expect(ExpectedCount.times(3), requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        server.expect(ExpectedCount.times(3), requestTo(URL))
                .andRespond(withSuccess("{\"status\":\"FAIL\"}", MediaType.APPLICATION_JSON));

        for (int i = 0; i < 6; i++) {
            client.evaluateRisk(request());
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        server.verify();
    }

    @Test
    @DisplayName("[TC-RISK-203] 차단 후 시험 호출이 성공하면 정상(CLOSED)으로 돌아오고 결과를 사용")
    void halfOpen_successfulTrial_closesCircuit() {
        circuitBreaker.transitionToOpenState();
        circuitBreaker.transitionToHalfOpenState();
        server.expect(requestTo(URL)).andRespond(withSuccess(RiskWatchClientTest.SUCCESS_BODY,
                MediaType.APPLICATION_JSON));

        RiskEvaluationResponse res = client.evaluateRisk(request());

        assertThat(res.isAvailable()).isTrue();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        server.verify();
    }
}
