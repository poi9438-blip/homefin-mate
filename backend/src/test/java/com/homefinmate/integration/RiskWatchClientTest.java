package com.homefinmate.integration;

import com.homefinmate.dto.risk.RiskEvaluationRequest;
import com.homefinmate.dto.risk.RiskEvaluationResponse;
import com.homefinmate.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

// RiskWatchClient 성공, 재시도, fallback 경로 검증 (가짜 HTTP 서버 사용)
@ExtendWith(MockitoExtension.class)
class RiskWatchClientTest {

        private static final String URL = "http://riskwatch.test/api/v1/risk/evaluate";

        @Mock
        AuditService auditService;

        private MockRestServiceServer server;
        private RiskWatchClient client;

        @BeforeEach
        void setUp() {
                RestTemplate restTemplate = new RestTemplate();
                server = MockRestServiceServer.bindTo(restTemplate).build();
                client = new RiskWatchClient(restTemplate, auditService);
                ReflectionTestUtils.setField(client, "riskWatchBaseUrl", "http://riskwatch.test");
                ReflectionTestUtils.setField(client, "riskWatchRetryCount", 2);
        }

        private RiskEvaluationRequest request() {
                return RiskEvaluationRequest.builder()
                                .userId("7")
                                .loanAmount(new BigDecimal("300000000"))
                                .monthlyIncome(new BigDecimal("4000000"))
                                .dsr(new BigDecimal("0.38"))
                                .region("부산")
                                .houseOwnership("NONE")
                                .sourceSystem("HOMEFIT_MATE")
                                .traceId("hfm-risk-1")
                                .build();
        }

        private static final String SUCCESS_BODY = """
                        {"status":"SUCCESS","data":{"riskLevel":"HIGH","riskScore":72,
                         "reason":"Triggered rules: DSR_HIGH","traceId":"hfm-risk-1"}}
                        """;

        // 성공: 주소, 헤더, 본문 확인, RiskWatch 응답을 결과로 변환 (reason => recommendation)
        @Test
        @DisplayName("성공 응답을 결과로 변환하고, 헤더와 본문을 올바르게 보냄")
        void success_mapsResponse() {
                server.expect(requestTo(URL))
                                .andExpect(method(HttpMethod.POST))
                                .andExpect(header("X-Trace-Id", "hfm-risk-1"))
                                .andExpect(header("X-Source-System", "HOMEFIT_MATE"))
                                .andExpect(header("X-Event-Version", "v1"))
                                .andExpect(jsonPath("$.userId").value("7"))
                                .andExpect(jsonPath("$.monthlyIncome").value(4000000))
                                .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

                RiskEvaluationResponse res = client.evaluateRisk(request());

                assertThat(res.isAvailable()).isTrue();
                assertThat(res.getRiskLevel()).isEqualTo("HIGH");
                assertThat(res.getRiskScore()).isEqualTo(72);
                assertThat(res.getRecommendation()).isEqualTo("Triggered rules: DSR_HIGH");
                server.verify();
                verifyNoInteractions(auditService);
        }

        // 두 번 실패 후 세 번째 성공: 결과 정상, 감사 기록 없음
        @Test
        @DisplayName("두 번 실패해도 세 번째에 성공하면 정상 결과")
        void retry_thenSuccess() {
                server.expect(ExpectedCount.twice(), requestTo(URL))
                                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
                server.expect(requestTo(URL))
                                .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

                RiskEvaluationResponse res = client.evaluateRisk(request());

                assertThat(res.isAvailable()).isTrue();
                assertThat(res.getRiskScore()).isEqualTo(72);
                server.verify();
                verifyNoInteractions(auditService);
        }

        // 3번 모두 연결 실패: fallback(UNKNOWN, 0점, available=false) + SYSTEM 감사 기록
        @Test
        @DisplayName("3번 모두 연결 실패하면 fallback과 감사 기록")
        void allFail_returnsFallbackAndAudits() {
                server.expect(ExpectedCount.times(3), requestTo(URL))
                                .andRespond(withException(new IOException("Connection refused")));

                RiskEvaluationResponse res = client.evaluateRisk(request());

                assertThat(res.isAvailable()).isFalse();
                assertThat(res.getRiskLevel()).isEqualTo("UNKNOWN");
                assertThat(res.getRiskScore()).isZero();
                assertThat(res.getTraceId()).isEqualTo("hfm-risk-1");
                server.verify();
                verify(auditService).logAudit(eq("SYSTEM"), eq("HOMEFIT_MATE"),
                                startsWith("RISKWATCH_INTEGRATION_FAILURE: "), eq("hfm-risk-1"));
        }

        // 200이지만 ERROR 응답: 3번 시도 후 fallback
        @Test
        @DisplayName("200이라도 SUCCESS가 아니면 재시도 후 fallback")
        void nonSuccessBody_retriesThenFallback() {
                server.expect(ExpectedCount.times(3), requestTo(URL))
                                .andRespond(withSuccess("{\"status\":\"ERROR\",\"message\":\"rule engine down\"}",
                                                MediaType.APPLICATION_JSON));

                RiskEvaluationResponse res = client.evaluateRisk(request());

                assertThat(res.isAvailable()).isFalse();
                server.verify();
        }

        // TODO: 수정 필요
        @Test
        @DisplayName("400 요청 오류도 3번 재시도함")
        void badRequest_isRetriedThreeTimes() {
                server.expect(ExpectedCount.times(3), requestTo(URL))
                                .andRespond(withBadRequest());

                RiskEvaluationResponse res = client.evaluateRisk(request());

                assertThat(res.isAvailable()).isFalse();
                server.verify();
        }
}
