package com.homefinmate.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homefinmate.common.TraceIds;
import com.homefinmate.repository.UserApplicationRepository;
import com.homefinmate.repository.UserRepository;
import com.homefinmate.support.ApiTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.util.List;
import java.util.UUID;

import static com.homefinmate.support.ApiTestSupport.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 대출 신청 HTTP + DB 통합 테스트 (FinFlow는 가짜 응답: FinFlow 전용 RestTemplate에 MockRestServiceServer)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationApiTest {

    // application-test.yml의 FinFlow 주소 (닫힌 포트, 여기서는 가짜 응답으로 대신함)
    static final String FINFLOW = "http://127.0.0.1:9/api/applications";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    UserRepository userRepository;
    @Autowired
    UserApplicationRepository applicationRepository;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    @Qualifier("finFlowRestTemplate")
    RestTemplate finFlowRestTemplate;

    private ApiTestSupport api;
    private MockRestServiceServer finflow;
    private String email;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestSupport(mockMvc, objectMapper);
        email = ApiTestSupport.uniqueEmail("apply");
        token = api.signupAndLogin(email);
        finflow = MockRestServiceServer.bindTo(finFlowRestTemplate).build();
    }

    @AfterEach
    void tearDown() {
        finflow.reset();
    }

    private Long userId(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    // 다른 사용자 가입 → id
    private Long otherUser(String prefix) throws Exception {
        String other = ApiTestSupport.uniqueEmail(prefix);
        api.signupAndLogin(other);
        return userId(other);
    }

    // 보금자리론이 "충족"인 통합 분석 입력 (TC-ANLZ-101과 같은 값)
    static String analysis() {
        return "{\"loanAmount\":300000000,\"annualInterestRate\":4.5,\"termMonths\":360,"
                + "\"monthlyIncome\":4000000,\"propertyType\":\"NONE\",\"region\":\"부산\","
                + "\"housePrice\":600000000,\"householdAnnualIncome\":48000000,"
                + "\"firstHome\":true,\"newlywed\":false,\"childCount\":0,"
                + "\"newbornWithin2Years\":false,\"dualIncome\":false}";
    }

    private static String applyBody(Long simulationId) {
        return "{\"productCode\":\"BOGEUMJARI\"," + (simulationId == null ? "" : "\"simulationId\":" + simulationId + ",")
                + "\"analysis\":" + analysis() + "}";
    }

    private MvcResult apply(String token, String key, String body) throws Exception {
        var request = post("/api/applications").header("Authorization", bearer(token))
                .header(TraceIds.HEADER, "hfm-apply-it").contentType(MediaType.APPLICATION_JSON).content(body);
        if (key != null) {
            request = request.header("Idempotency-Key", key);
        }
        return mockMvc.perform(request).andReturn();
    }

    // 통합 분석으로 계산 기록 하나 만들고 id (RiskWatch는 닫힌 포트라 fallback)
    private Long simulation(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/analyze").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(analysis())).andReturn();
        return api.read(result).at("/data/loan/simulationId").asLong();
    }

    static String receipt(String applicationId) {
        return "{\"status\":\"SUCCESS\",\"data\":{\"applicationId\":\"" + applicationId
                + "\",\"status\":\"RECEIVED\",\"receivedAt\":\"2026-10-27T01:02:03.123456Z\"}}";
    }

    static String view(String applicationId, String status) {
        return "{\"status\":\"SUCCESS\",\"data\":{\"applicationId\":\"" + applicationId + "\",\"productCode\":\"BOGEUMJARI\","
                + "\"status\":\"" + status + "\",\"requestedAmount\":300000000,\"termMonths\":360,"
                + "\"receivedAt\":\"2026-10-27T01:02:03Z\",\"decidedAt\":null,\"rejectReason\":null}}";
    }

    private long rows(Long userId) {
        return jdbc.queryForObject("select count(*) from user_applications where user_id = ?", Long.class, userId);
    }

    @Test
    @DisplayName("[TC-APPLY-101] 추천 상품으로 신청 → 201 {applicationId, productCode, RECEIVED, receivedAt}, user_applications 1줄, 이용 기록 LOAN_APPLICATION, FinFlow에 신청 값·simulationRef·같은 키")
    void apply_created() throws Exception {
        Long userId = userId(email);
        Long simulationId = simulation(token);
        String key = UUID.randomUUID().toString();
        String applicationId = UUID.randomUUID().toString();
        finflow.expect(ExpectedCount.once(), requestTo(FINFLOW))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", key))
                .andExpect(header("X-Trace-Id", "hfm-apply-it"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.applicantRef")
                        .value("homefin:" + userId))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.simulationRef")
                        .value(simulationId))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.dti")
                        .value(0.3801))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body(receipt(applicationId)));

        MvcResult result = apply(token, key, applyBody(simulationId));

        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
        JsonNode data = api.read(result).get("data");
        assertThat(data.get("applicationId").asText()).isEqualTo(applicationId);
        assertThat(data.get("productCode").asText()).isEqualTo("BOGEUMJARI");
        assertThat(data.get("status").asText()).isEqualTo("RECEIVED");
        assertThat(data.get("receivedAt").asText()).endsWith("Z");
        assertThat(rows(userId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select simulation_id from user_applications where application_id = ?",
                Long.class, applicationId)).isEqualTo(simulationId);
        assertThat(jdbc.queryForObject("select count(*) from user_activity_logs where user_id = ? and action = ?",
                Long.class, userId, "LOAN_APPLICATION")).isEqualTo(1);
        finflow.verify();
    }

    @Test
    @DisplayName("[TC-APPLY-102] 같은 Idempotency-Key로 다시 신청 → 200, 같은 신청 번호, user_applications는 그대로 1줄")
    void sameKey_replayed() throws Exception {
        Long userId = userId(email);
        String key = UUID.randomUUID().toString();
        String applicationId = UUID.randomUUID().toString();
        finflow.expect(ExpectedCount.once(), requestTo(FINFLOW)).andExpect(header("Idempotency-Key", key))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body(receipt(applicationId)));
        // FinFlow는 같은 키를 처음 결과로 다시 보냄 (200 + Idempotent-Replayed)
        finflow.expect(ExpectedCount.once(), requestTo(FINFLOW)).andExpect(header("Idempotency-Key", key))
                .andRespond(withSuccess(receipt(applicationId), MediaType.APPLICATION_JSON)
                        .header("Idempotent-Replayed", "true"));

        MvcResult first = apply(token, key, applyBody(null));
        MvcResult second = apply(token, key, applyBody(null));

        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        assertThat(api.read(second).at("/data/applicationId").asText()).isEqualTo(applicationId);
        assertThat(api.read(second).at("/data/replayed").asBoolean()).isTrue();
        assertThat(rows(userId)).isEqualTo(1);
        // 재전송은 새 신청이 아니라 이용 기록도 1줄
        assertThat(jdbc.queryForObject("select count(*) from user_activity_logs where user_id = ? and action = ?",
                Long.class, userId, "LOAN_APPLICATION")).isEqualTo(1);
        finflow.verify();
    }

    @Test
    @DisplayName("[TC-APPLY-103] Idempotency-Key 없음 / 7자 / 허용하지 않는 문자 → 400, FinFlow를 부르지 않음")
    void invalidIdempotencyKey_badRequest() throws Exception {
        for (String key : new String[] { null, "abc1234", "key with space!" }) {
            MvcResult result = apply(token, key, applyBody(null));
            assertThat(result.getResponse().getStatus()).as(String.valueOf(key)).isEqualTo(400);
            assertThat(api.read(result).get("message").asText()).contains("Idempotency-Key");
        }
        assertThat(rows(userId(email))).isZero();
        finflow.verify();
    }

    @Test
    @DisplayName("[TC-APPLY-104] 다른 사용자의 계산 기록 id로 신청 → 404, FinFlow를 부르지 않음")
    void otherUsersSimulation_notFound() throws Exception {
        String otherToken = api.signupAndLogin(ApiTestSupport.uniqueEmail("apply-other"));
        Long othersSimulation = simulation(otherToken);

        MvcResult result = apply(token, UUID.randomUUID().toString(), applyBody(othersSimulation));

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(api.read(result).get("message").asText()).isEqualTo("Simulation not found");
        assertThat(rows(userId(email))).isZero();
        finflow.verify();
    }

    @Test
    @DisplayName("[TC-APPLY-105] FinFlow 응답 없음 → 503 Application service unavailable / FinFlow 409 → 409 그대로, 둘 다 저장 안 함")
    void finflowFailures() throws Exception {
        // 테스트 설정은 재시도 0번 (재시도는 TC-APPLY-202)
        finflow.expect(ExpectedCount.once(), requestTo(FINFLOW))
                .andRespond(withException(new ConnectException("Connection refused")));
        finflow.expect(ExpectedCount.once(), requestTo(FINFLOW))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":\"ERROR\",\"message\":\"Active application already exists\",\"data\":null}"));

        MvcResult down = apply(token, UUID.randomUUID().toString(), applyBody(null));
        MvcResult conflict = apply(token, UUID.randomUUID().toString(), applyBody(null));

        assertThat(down.getResponse().getStatus()).isEqualTo(503);
        assertThat(api.read(down).get("message").asText()).isEqualTo("Application service unavailable");
        assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
        assertThat(api.read(conflict).get("message").asText()).isEqualTo("Active application already exists");
        assertThat(rows(userId(email))).isZero();
        finflow.verify();
    }

    @Test
    @DisplayName("[TC-APPLY-106] 내 신청 목록: 내 신청만 최신순, 상태는 FinFlow 값, FinFlow가 한 건을 답하지 못하면 그 건만 available=false")
    void list_ownApplicationsWithFinFlowStatus() throws Exception {
        Long userId = userId(email);
        Long otherId = otherUser("apply-list-other");
        String older = UUID.randomUUID().toString();
        String newer = UUID.randomUUID().toString();
        insertRow(userId, older, "2026-10-01 00:00:00");
        insertRow(userId, newer, "2026-10-02 00:00:00");
        insertRow(otherId, UUID.randomUUID().toString(), "2026-10-03 00:00:00");
        finflow.expect(ExpectedCount.once(), requestTo(FINFLOW + "/" + newer + "?applicantRef=homefin:" + userId))
                .andRespond(withSuccess(view(newer, "REVIEWING"), MediaType.APPLICATION_JSON));
        finflow.expect(ExpectedCount.once(), requestTo(FINFLOW + "/" + older + "?applicantRef=homefin:" + userId))
                .andRespond(withException(new ConnectException("Connection refused")));

        MvcResult result = mockMvc.perform(get("/api/applications").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();

        JsonNode data = api.read(result).get("data");
        assertThat(data).hasSize(2);
        assertThat(data.get(0).get("applicationId").asText()).isEqualTo(newer);
        assertThat(data.get(0).get("status").asText()).isEqualTo("REVIEWING");
        assertThat(data.get(0).get("available").asBoolean()).isTrue();
        assertThat(data.get(1).get("applicationId").asText()).isEqualTo(older);
        assertThat(data.get(1).get("available").asBoolean()).isFalse();
        assertThat(data.get(1).get("status").isNull()).isTrue();
        finflow.verify();
    }

    @Test
    @DisplayName("[TC-APPLY-107] 취소: 다른 사용자의 신청 번호는 404(FinFlow를 부르지 않음), 내 신청은 FinFlow 취소 결과 CANCELLED")
    void cancel_ownOnly() throws Exception {
        Long userId = userId(email);
        Long otherId = otherUser("apply-cancel-other");
        String mine = UUID.randomUUID().toString();
        String others = UUID.randomUUID().toString();
        insertRow(userId, mine, "2026-10-01 00:00:00");
        insertRow(otherId, others, "2026-10-01 00:00:00");
        finflow.expect(ExpectedCount.once(), requestTo(FINFLOW + "/" + mine + "/cancel"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.applicantRef")
                        .value("homefin:" + userId))
                .andRespond(withSuccess(view(mine, "CANCELLED"), MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/api/applications/" + others + "/cancel").header("Authorization", bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Application not found"));
        mockMvc.perform(post("/api/applications/" + mine + "/cancel").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        finflow.verify();
    }

    private void insertRow(Long userId, String applicationId, String createdAt) {
        jdbc.update("insert into user_applications (user_id, application_id, product_code, created_at)"
                + " values (?, ?, 'BOGEUMJARI', ?)", userId, applicationId, java.sql.Timestamp.valueOf(createdAt));
    }
}
