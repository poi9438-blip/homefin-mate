package com.homefinmate.integration;

import com.homefinmate.dto.risk.RiskEvaluationRequest;
import com.homefinmate.dto.risk.RiskEvaluationResponse;
import com.homefinmate.service.AuditService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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

        private static final String URL = "http://riskwatch.test/api/evaluations";
        static final String TOKEN_SECRET = "HomeFin-Test-RiskWatch-Token-Secret-32bytes";
        static final String JWT_SECRET = "HomeFin-Test-Secret-Key-Must-Be-At-Least-256-Bits-Long";

        @Mock
        AuditService auditService;

        private MockRestServiceServer server;
        private RiskWatchClient client;

        // 테스트에서 쓰는 클라이언트 (재시도 대기 없음)
        static RiskWatchClient client(RestTemplate restTemplate, AuditService auditService,
                        CircuitBreaker circuitBreaker, int retryCount) {
                RiskWatchClient client = new RiskWatchClient(restTemplate, auditService, circuitBreaker,
                                new RiskWatchTokens(TOKEN_SECRET, JWT_SECRET));
                ReflectionTestUtils.setField(client, "riskWatchBaseUrl", "http://riskwatch.test");
                ReflectionTestUtils.setField(client, "riskWatchRetryCount", retryCount);
                ReflectionTestUtils.setField(client, "backoffMs", 0L);
                return client;
        }

        // RiskWatch 평가 입력 (traceId는 헤더로만)
        static RiskEvaluationRequest request(String traceId) {
                return RiskEvaluationRequest.builder()
                                .productCode("BOGEUMJARI")
                                .applicantRef("homefin:7")
                                .requestedAmount(300_000_000L)
                                .termMonths(360)
                                .housePrice(600_000_000L)
                                .householdAnnualIncome(48_000_000L)
                                .dti(new BigDecimal("0.3801"))
                                .houseOwnership("NONE")
                                .firstHome(true)
                                .childCount(0)
                                .region("부산")
                                .traceId(traceId)
                                .build();
        }

        private RiskEvaluationRequest request() {
                return request("hfm-risk-1");
        }

        // RiskWatch 평가 API 성공 응답 (판정 REVIEW, 근거 1개만 줄여서)
        static final String SUCCESS_BODY = """
                        {"status":"SUCCESS","message":"OK","traceId":"hfm-risk-1","data":{
                         "evaluationId":"11111111-2222-3333-4444-555555555555","sourceSystem":"HOMEFIN",
                         "productCode":"BOGEUMJARI","verdict":"REVIEW","riskLevel":"MEDIUM","riskScore":7,
                         "recommendation":"검토 필요: 주택 보유","criteriaVersion":"2026-10-26",
                         "reasons":[{"code":"DTI_LIMIT","name":"DTI","threshold":0.6000,"actual":0.3801,
                                     "result":"PASS","detail":"DTI 38.01% ≤ 60%"}],
                         "notEvaluated":["신용점수","순자산"],"createdAt":"2026-10-27T01:02:03.123456Z"}}
                        """;

        @Test
        @DisplayName("[TC-RISK-204] RiskWatch 평가 API 성공: POST /api/evaluations, 서비스 토큰·X-Trace-Id, 평가 입력 15개(요청 서버 없음), 판정·근거·기준 버전으로 변환")
        void evaluationApi_success() {
                server.expect(requestTo(URL))
                                .andExpect(method(HttpMethod.POST))
                                .andExpect(header("X-Trace-Id", "hfm-risk-1"))
                                .andExpect(header("Authorization", org.hamcrest.Matchers.startsWith("Bearer ")))
                                .andExpect(headerDoesNotExist("X-Source-System"))
                                .andExpect(headerDoesNotExist("X-Event-Version"))
                                .andExpect(jsonPath("$.productCode").value("BOGEUMJARI"))
                                .andExpect(jsonPath("$.applicantRef").value("homefin:7"))
                                .andExpect(jsonPath("$.requestedAmount").value(300000000))
                                .andExpect(jsonPath("$.termMonths").value(360))
                                .andExpect(jsonPath("$.housePrice").value(600000000))
                                .andExpect(jsonPath("$.householdAnnualIncome").value(48000000))
                                .andExpect(jsonPath("$.dti").value(0.3801))
                                .andExpect(jsonPath("$.houseOwnership").value("NONE"))
                                .andExpect(jsonPath("$.firstHome").value(true))
                                .andExpect(jsonPath("$.newlywed").value(false))
                                .andExpect(jsonPath("$.newbornWithin2Years").value(false))
                                .andExpect(jsonPath("$.dualIncome").value(false))
                                .andExpect(jsonPath("$.childCount").value(0))
                                .andExpect(jsonPath("$.region").value("부산"))
                                // 요청 서버·traceId·예전 칸은 본문에 없음
                                .andExpect(jsonPath("$.sourceSystem").doesNotExist())
                                .andExpect(jsonPath("$.traceId").doesNotExist())
                                .andExpect(jsonPath("$.dsr").doesNotExist())
                                .andExpect(jsonPath("$.userId").doesNotExist())
                                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                                                .body(SUCCESS_BODY));

                RiskEvaluationResponse res = client.evaluateRisk(request());

                assertThat(res.getStatus()).isEqualTo(RiskEvaluationResponse.Status.EVALUATED);
                assertThat(res.isAvailable()).isTrue();
                assertThat(res.getEvaluationId()).isEqualTo("11111111-2222-3333-4444-555555555555");
                assertThat(res.getProductCode()).isEqualTo("BOGEUMJARI");
                assertThat(res.getVerdict()).isEqualTo("REVIEW");
                assertThat(res.getCriteriaVersion()).isEqualTo("2026-10-26");
                assertThat(res.getRiskLevel()).isEqualTo("MEDIUM");
                assertThat(res.getRiskScore()).isEqualTo(7);
                assertThat(res.getRecommendation()).isEqualTo("검토 필요: 주택 보유");
                assertThat(res.getReasons()).singleElement().satisfies(r -> {
                        assertThat(r.code()).isEqualTo("DTI_LIMIT");
                        assertThat(r.threshold()).isEqualByComparingTo("0.6");
                        assertThat(r.actual()).isEqualByComparingTo("0.3801");
                        assertThat(r.result()).isEqualTo("PASS");
                        assertThat(r.detail()).isEqualTo("DTI 38.01% ≤ 60%");
                });
                assertThat(res.getNotEvaluated()).containsExactly("신용점수", "순자산");
                assertThat(res.getTraceId()).isEqualTo("hfm-risk-1");
                server.verify();
                verifyNoInteractions(auditService);
        }

        @BeforeEach
        void setUp() {
                RestTemplate restTemplate = new RestTemplate();
                server = MockRestServiceServer.bindTo(restTemplate).build();
                client = client(restTemplate, auditService, CircuitBreaker.ofDefaults("test"), 2);
        }

        // 두 번 실패 후 세 번째 성공: 결과 정상, 감사 기록 없음
        @Test
        @DisplayName("[TC-RISK-002] 두 번 실패해도 세 번째에 성공하면 정상 결과")
        void retry_thenSuccess() {
                server.expect(ExpectedCount.twice(), requestTo(URL))
                                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
                server.expect(requestTo(URL))
                                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                                                .body(SUCCESS_BODY));

                RiskEvaluationResponse res = client.evaluateRisk(request());

                assertThat(res.isAvailable()).isTrue();
                assertThat(res.getRiskScore()).isEqualTo(7);
                server.verify();
                verifyNoInteractions(auditService);
        }

        // 3번 모두 연결 실패: fallback(UNKNOWN, 0점, available=false) + SYSTEM 감사 기록
        @Test
        @DisplayName("[TC-RISK-003] 3번 모두 연결 실패하면 fallback과 감사 기록")
        void allFail_returnsFallbackAndAudits() {
                server.expect(ExpectedCount.times(3), requestTo(URL))
                                .andRespond(withException(new ConnectException("Connection refused")));

                RiskEvaluationResponse res = client.evaluateRisk(request());

                assertThat(res.isAvailable()).isFalse();
                assertThat(res.getStatus()).isEqualTo(RiskEvaluationResponse.Status.UNAVAILABLE);
                assertThat(res.getRiskLevel()).isEqualTo("UNKNOWN");
                assertThat(res.getRiskScore()).isZero();
                assertThat(res.getTraceId()).isEqualTo("hfm-risk-1");
                server.verify();
                verify(auditService).logAudit(eq("SYSTEM"), eq(RiskWatchClient.SOURCE_SYSTEM),
                                startsWith("RISKWATCH_INTEGRATION_FAILURE: "), eq("hfm-risk-1"));
        }

        // 200이지만 ERROR 응답: RiskWatch가 처리할 수 없다고 답한 것이라 재시도 없이 fallback
        @Test
        @DisplayName("[TC-RISK-004] 200이라도 SUCCESS가 아니면 재시도 없이 fallback")
        void nonSuccessBody_noRetry() {
                server.expect(ExpectedCount.once(), requestTo(URL))
                                .andRespond(withSuccess("{\"status\":\"ERROR\",\"message\":\"rule engine down\"}",
                                                MediaType.APPLICATION_JSON));

                RiskEvaluationResponse res = client.evaluateRisk(request());

                assertThat(res.isAvailable()).isFalse();
                server.verify();
        }

        // 400 요청 오류: 다시 보내도 같으므로 1번만 보내고 fallback
        @Test
        @DisplayName("[TC-RISK-005] 400 요청 오류는 재시도하지 않음")
        void badRequest_noRetry() {
                server.expect(ExpectedCount.once(), requestTo(URL))
                                .andRespond(withBadRequest());

                RiskEvaluationResponse res = client.evaluateRisk(request());

                assertThat(res.isAvailable()).isFalse();
                server.verify();
        }

        // 응답 타임아웃: 상대가 멈춘 상태라 재시도하면 대기만 늘어남 => 1번만
        @Test
        @DisplayName("[TC-RISK-006] 응답 타임아웃은 재시도하지 않음")
        void readTimeout_noRetry() {
                server.expect(ExpectedCount.once(), requestTo(URL))
                                .andRespond(withException(new SocketTimeoutException("Read timed out")));

                RiskEvaluationResponse res = client.evaluateRisk(request());

                assertThat(res.isAvailable()).isFalse();
                server.verify();
        }

        // RestClientException이 아닌 예외(설정 오류 등)도 fallback
        @Test
        @DisplayName("[TC-RISK-007] 예상하지 못한 예외도 분석을 멈추지 않고 fallback (#42)")
        void unexpectedException_returnsFallback() {
                server.expect(ExpectedCount.once(), requestTo(URL))
                                .andRespond(req -> {
                                        throw new IllegalArgumentException("The port must be an integer");
                                });

                RiskEvaluationResponse res = client.evaluateRisk(request());

                assertThat(res.isAvailable()).isFalse();
                server.verify();
                verify(auditService).logAudit(eq("SYSTEM"), eq(RiskWatchClient.SOURCE_SYSTEM),
                                startsWith("RISKWATCH_INTEGRATION_FAILURE: IllegalArgumentException"),
                                eq("hfm-risk-1"));
        }

        // 판정의 핵심 칸이 하나라도 빠진 성공 응답은 결과로 쓰지 않음 (SUCCESS가 아닌 응답과 같은 경로)
        @Test
        @DisplayName("[TC-RISK-205] 성공 응답에 평가 번호·판정·기준 버전·위험 등급·점수 중 하나가 없으면 재시도 없이 fallback")
        void missingVerdictFields_fallbackWithoutRetry() {
                List<String> bodies = new ArrayList<>();
                for (String field : List.of("\"evaluationId\":\"11111111-2222-3333-4444-555555555555\",",
                                "\"verdict\":\"REVIEW\",", "\"criteriaVersion\":\"2026-10-26\",",
                                "\"riskLevel\":\"MEDIUM\",", "\"riskScore\":7,")) {
                        assertThat(SUCCESS_BODY).contains(field);
                        bodies.add(SUCCESS_BODY.replace(field, ""));
                }
                for (String body : bodies) {
                        server.expect(ExpectedCount.once(), requestTo(URL))
                                        .andRespond(withStatus(HttpStatus.CREATED)
                                                        .contentType(MediaType.APPLICATION_JSON).body(body));
                }

                for (int i = 0; i < bodies.size(); i++) {
                        RiskEvaluationResponse res = client.evaluateRisk(request());
                        assertThat(res.getStatus()).as("빠진 칸 %d", i).isEqualTo(RiskEvaluationResponse.Status.UNAVAILABLE);
                        assertThat(res.getEvaluationId()).isNull();
                }
                // 응답 5개에 요청 5번 (재시도 없음)
                server.verify();
        }

        @Test
        @DisplayName("[TC-RISK-206] RiskWatch로 보낸 서비스 토큰: RISKWATCH_TOKEN_SECRET 서명, iss=homefin-mate, aud=riskwatch, 유효 기간 5분 이하, 재시도마다 새로 발급")
        void serviceToken_signedAndFreshPerAttempt() {
                // 발급 횟수를 세려고 실제 발급기를 감쌈
                RestTemplate restTemplate = new RestTemplate();
                server = MockRestServiceServer.bindTo(restTemplate).build();
                RiskWatchTokens issuer = org.mockito.Mockito.spy(new RiskWatchTokens(TOKEN_SECRET, JWT_SECRET));
                client = new RiskWatchClient(restTemplate, auditService, CircuitBreaker.ofDefaults("test"), issuer);
                ReflectionTestUtils.setField(client, "riskWatchBaseUrl", "http://riskwatch.test");
                ReflectionTestUtils.setField(client, "riskWatchRetryCount", 2);
                ReflectionTestUtils.setField(client, "backoffMs", 0L);
                List<String> tokens = new ArrayList<>();
                server.expect(ExpectedCount.once(), requestTo(URL))
                                .andExpect(req -> tokens.add(req.getHeaders().getFirst("Authorization")))
                                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
                server.expect(ExpectedCount.once(), requestTo(URL))
                                .andExpect(req -> tokens.add(req.getHeaders().getFirst("Authorization")))
                                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                                                .body(SUCCESS_BODY));

                assertThat(client.evaluateRisk(request()).isAvailable()).isTrue();

                assertThat(tokens).hasSize(2);
                for (String authorization : tokens) {
                        assertThat(authorization).startsWith("Bearer ");
                        Claims claims = Jwts.parser()
                                        .verifyWith(Keys.hmacShaKeyFor(TOKEN_SECRET.getBytes(StandardCharsets.UTF_8)))
                                        .requireIssuer("homefin-mate")
                                        .requireAudience("riskwatch")
                                        .build()
                                        .parseSignedClaims(authorization.substring(7))
                                        .getPayload();
                        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime())
                                        .isPositive().isLessThanOrEqualTo(5 * 60 * 1000L);
                }
                // 시도마다 새로 발급 (같은 초 안이면 토큰 문자열이 같을 수 있어 발급 횟수로 확인)
                verify(issuer, org.mockito.Mockito.times(2)).issue();
                server.verify();
        }
}
