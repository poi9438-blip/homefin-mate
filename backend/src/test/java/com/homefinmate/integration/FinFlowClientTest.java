package com.homefinmate.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homefinmate.dto.application.FinFlowApplicationRequest;
import com.homefinmate.integration.FinFlowClient.Failure;
import com.homefinmate.integration.FinFlowClient.FinFlowException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

// FinFlowClient: 신청 접수(같은 키 재시도)·조회·취소, 서비스 토큰 (가짜 HTTP 서버)
class FinFlowClientTest {

    static final String URL = "http://finflow.test/api/applications";
    static final String TOKEN_SECRET = "HomeFin-Test-FinFlow-Token-Secret-32bytes!!";
    static final String JWT_SECRET = "HomeFin-Test-Secret-Key-Must-Be-At-Least-256-Bits-Long";
    static final String RISKWATCH_SECRET = "HomeFin-Test-RiskWatch-Token-Secret-32bytes";
    static final String KEY = "6f0a7c5e-9d2b-4c1a-8e3f-1b2c3d4e5f60";
    static final String APP_ID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

    private MockRestServiceServer server;
    private FinFlowClient client;

    static FinFlowClient client(RestTemplate restTemplate, int retryCount) {
        FinFlowClient client = new FinFlowClient(restTemplate,
                new FinFlowTokens(TOKEN_SECRET, JWT_SECRET, RISKWATCH_SECRET), new ObjectMapper().findAndRegisterModules());
        ReflectionTestUtils.setField(client, "baseUrl", "http://finflow.test");
        ReflectionTestUtils.setField(client, "retryCount", retryCount);
        ReflectionTestUtils.setField(client, "backoffMs", 0L);
        return client;
    }

    static FinFlowApplicationRequest body() {
        return new FinFlowApplicationRequest("BOGEUMJARI", "homefin:7", 300_000_000L, 360, 600_000_000L,
                48_000_000L, new BigDecimal("0.3801"), true, false, false, 0, "NONE", false, "부산", 42L);
    }

    static String receipt() {
        return "{\"status\":\"SUCCESS\",\"message\":\"OK\",\"data\":{\"applicationId\":\"" + APP_ID
                + "\",\"status\":\"RECEIVED\",\"receivedAt\":\"2026-10-27T01:02:03.123456Z\"},\"traceId\":\"x\"}";
    }

    static ResponseCreator created() {
        return withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(receipt());
    }

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = client(restTemplate, 2);
    }

    @Test
    @DisplayName("[TC-APPLY-201] FinFlow 신청 접수: POST /api/applications, 서비스 토큰·Idempotency-Key·X-Trace-Id, 본문 값, 접수 응답 변환")
    void receive_sendsHeadersAndBody() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", KEY))
                .andExpect(header("X-Trace-Id", "hfm-apply-1"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, org.hamcrest.Matchers.startsWith("Bearer ")))
                .andExpect(jsonPath("$.productCode").value("BOGEUMJARI"))
                .andExpect(jsonPath("$.applicantRef").value("homefin:7"))
                .andExpect(jsonPath("$.requestedAmount").value(300000000))
                .andExpect(jsonPath("$.termMonths").value(360))
                .andExpect(jsonPath("$.housePrice").value(600000000))
                .andExpect(jsonPath("$.householdAnnualIncome").value(48000000))
                .andExpect(jsonPath("$.dti").value(0.3801))
                .andExpect(jsonPath("$.firstHome").value(true))
                .andExpect(jsonPath("$.newlywed").value(false))
                .andExpect(jsonPath("$.newbornWithin2Years").value(false))
                .andExpect(jsonPath("$.childCount").value(0))
                .andExpect(jsonPath("$.houseOwnership").value("NONE"))
                .andExpect(jsonPath("$.dualIncome").value(false))
                .andExpect(jsonPath("$.region").value("부산"))
                .andExpect(jsonPath("$.simulationRef").value(42))
                .andRespond(created());

        FinFlowClient.Received received = client.receive(body(), KEY, "hfm-apply-1");

        assertThat(received.applicationId()).isEqualTo(APP_ID);
        assertThat(received.status()).isEqualTo("RECEIVED");
        assertThat(received.receivedAt()).hasToString("2026-10-27T01:02:03.123456Z");
        assertThat(received.replayed()).isFalse();
        server.verify();
    }

    @Test
    @DisplayName("[TC-APPLY-202] 재시도: 연결 실패·5xx·시간 초과는 같은 키로 최대 2번 더(총 3번), 4xx는 다시 보내지 않음")
    void receive_retriesWithSameKey() {
        List<String> keys = new ArrayList<>();
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andExpect(req -> keys.add(req.getHeaders().getFirst("Idempotency-Key")))
                .andRespond(withException(new ConnectException("Connection refused")));
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andExpect(req -> keys.add(req.getHeaders().getFirst("Idempotency-Key")))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));
        // 세 번째: 앞 요청이 이미 처리됐다면 FinFlow는 처음 결과를 다시 보냄 (200 + Idempotent-Replayed)
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andExpect(req -> keys.add(req.getHeaders().getFirst("Idempotency-Key")))
                .andRespond(withSuccess(receipt(), MediaType.APPLICATION_JSON).header("Idempotent-Replayed", "true"));

        FinFlowClient.Received received = client.receive(body(), KEY, "hfm-apply-2");
        assertThat(keys).containsExactly(KEY, KEY, KEY);
        assertThat(received.replayed()).isTrue();
        server.verify();

        // 3번 모두 5xx → UNAVAILABLE
        server.reset();
        server.expect(ExpectedCount.times(3), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> client.receive(body(), KEY, "hfm-apply-2"))
                .isInstanceOfSatisfying(FinFlowException.class, e -> assertThat(e.getFailure()).isEqualTo(Failure.UNAVAILABLE));
        server.verify();

        // 4xx는 1번만: 409는 FinFlow 메시지 그대로, 400은 REJECTED
        Map<HttpStatus, Failure> once = new LinkedHashMap<>();
        once.put(HttpStatus.CONFLICT, Failure.CONFLICT);
        once.put(HttpStatus.BAD_REQUEST, Failure.REJECTED);
        for (Map.Entry<HttpStatus, Failure> c : once.entrySet()) {
            server.reset();
            server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withStatus(c.getKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"status\":\"ERROR\",\"message\":\"Active application already exists\",\"data\":null}"));
            assertThatThrownBy(() -> client.receive(body(), KEY, "hfm-apply-2"))
                    .isInstanceOfSatisfying(FinFlowException.class, e -> {
                        assertThat(e.getFailure()).isEqualTo(c.getValue());
                        assertThat(e.getMessage()).isEqualTo("Active application already exists");
                    });
            server.verify();
        }
    }

    @Test
    @DisplayName("[TC-APPLY-203] FinFlow로 보낸 서비스 토큰: FINFLOW_TOKEN_SECRET 서명, iss=homefin-mate, aud=finflow, 유효 기간 5분 이하")
    void serviceToken() {
        List<String> tokens = new ArrayList<>();
        server.expect(requestTo(URL))
                .andExpect(req -> tokens.add(req.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)))
                .andRespond(created());
        client.receive(body(), KEY, "hfm-apply-3");

        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(TOKEN_SECRET.getBytes(StandardCharsets.UTF_8)))
                .requireIssuer("homefin-mate")
                .requireAudience("finflow")
                .build()
                .parseSignedClaims(tokens.get(0).substring(7))
                .getPayload();
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime())
                .isPositive().isLessThanOrEqualTo(5 * 60 * 1000L);
    }

    @Test
    @DisplayName("[TC-APPLY-201] FinFlow 조회·취소: applicantRef 전달, 404는 NOT_FOUND, 응답 없음은 UNAVAILABLE")
    void findAndCancel() {
        String view = "{\"status\":\"SUCCESS\",\"data\":{\"applicationId\":\"" + APP_ID + "\",\"productCode\":\"BOGEUMJARI\","
                + "\"status\":\"CANCELLED\",\"requestedAmount\":300000000,\"termMonths\":360,"
                + "\"receivedAt\":\"2026-10-27T01:02:03Z\",\"decidedAt\":null,\"rejectReason\":null}}";
        server.expect(requestTo(URL + "/" + APP_ID + "?applicantRef=homefin:7")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(view, MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL + "/" + APP_ID + "/cancel")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.applicantRef").value("homefin:7"))
                .andRespond(withSuccess(view, MediaType.APPLICATION_JSON));

        assertThat(client.find(APP_ID, "homefin:7", "t").status()).isEqualTo("CANCELLED");
        assertThat(client.cancel(APP_ID, "homefin:7", "t").requestedAmount()).isEqualTo(300_000_000L);
        server.verify();

        server.reset();
        server.expect(requestTo(URL + "/" + APP_ID + "?applicantRef=homefin:7")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(URL + "/" + APP_ID + "?applicantRef=homefin:7")).andRespond(withException(new ConnectException("x")));
        assertThatThrownBy(() -> client.find(APP_ID, "homefin:7", "t"))
                .isInstanceOfSatisfying(FinFlowException.class, e -> assertThat(e.getFailure()).isEqualTo(Failure.NOT_FOUND));
        assertThatThrownBy(() -> client.find(APP_ID, "homefin:7", "t"))
                .isInstanceOfSatisfying(FinFlowException.class, e -> assertThat(e.getFailure()).isEqualTo(Failure.UNAVAILABLE));
    }
}
