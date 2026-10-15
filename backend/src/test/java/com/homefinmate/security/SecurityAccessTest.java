package com.homefinmate.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homefinmate.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static com.homefinmate.support.ApiTestSupport.bearer;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 인증·인가 규칙 통합 테스트 (보안 필터 체인 포함)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAccessTest {

        @Autowired
        MockMvc mockMvc;
        @Autowired
        ObjectMapper objectMapper;
        @Autowired
        JwtUtil jwtUtil;

        @Value("${app.jwt.secret}")
        String jwtSecret;

        private ApiTestSupport api;

        @BeforeEach
        void setUp() {
                api = new ApiTestSupport(mockMvc, objectMapper);
        }

        @ParameterizedTest(name = "{0} {1}")
        @CsvSource({
                        "GET,  /api/loan/history",
                        "POST, /api/loan/calculate",
                        "POST, /api/analyze",
                        "GET,  /api/report/summary",
                        "GET,  /api/admin/audit-logs",
                        "GET,  /actuator/metrics"
        })
        @DisplayName("[TC-AUTH-101] 토큰 없이 보호된 API를 부르면 401 JSON")
        void protectedApi_withoutToken_returns401(String method, String path) throws Exception {
                mockMvc.perform(request(HttpMethod.valueOf(method), path)
                                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.status").value("ERROR"))
                                .andExpect(jsonPath("$.message").value("Unauthorized"));
        }

        @Test
        @DisplayName("[TC-AUTH-102] 공개 API(DSR, 정책 추천, health)는 토큰 없이 200")
        void publicApis_withoutToken_areAllowed() throws Exception {
                mockMvc.perform(post("/api/loan/dsr").contentType(MediaType.APPLICATION_JSON)
                                .content(api.json(Map.of("monthlyDebt", 380000, "grossMonthlyIncome", 1000000))))
                                .andExpect(status().isOk());
                mockMvc.perform(post("/api/policy/recommend").contentType(MediaType.APPLICATION_JSON).content("{}"))
                                .andExpect(status().isOk());
                mockMvc.perform(get("/actuator/health"))
                                .andExpect(status().isOk());
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                        "/api/admin/audit-logs", "/api/admin/login-failures",
                        "/api/admin/user-activity", "/api/admin/integration-failures" })
        @DisplayName("[TC-AUTH-103] USER 토큰으로 관리자 API를 부르면 403")
        void adminApi_withUserToken_returns403(String path) throws Exception {
                String userToken = api.signupAndLogin(ApiTestSupport.uniqueEmail("user"));

                mockMvc.perform(get(path).header("Authorization", bearer(userToken)))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.message").value("Access denied"));
        }

        @Test
        @DisplayName("[TC-AUTH-104] ADMIN 토큰이면 관리자 API 200")
        void adminApi_withAdminToken_returns200() throws Exception {
                String adminToken = jwtUtil.generateToken("admin@test.com", "ADMIN");

                mockMvc.perform(get("/api/admin/audit-logs").header("Authorization", bearer(adminToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("SUCCESS"));
        }

        @Test
        @DisplayName("[TC-AUTH-105] 역할을 ADMIN으로 바꾼 변조 토큰은 401")
        void tamperedToken_returns401() throws Exception {
                String userToken = api.signupAndLogin(ApiTestSupport.uniqueEmail("tamper"));
                String[] parts = userToken.split("\\.");
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                                .replace("\"USER\"", "\"ADMIN\"");
                String tampered = parts[0] + "."
                                + Base64.getUrlEncoder().withoutPadding()
                                                .encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                                + "." + parts[2];

                mockMvc.perform(get("/api/admin/audit-logs").header("Authorization", bearer(tampered)))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("[TC-AUTH-106] 만료된 토큰은 401")
        void expiredToken_returns401() throws Exception {
                JwtUtil expiredIssuer = new JwtUtil();
                ReflectionTestUtils.setField(expiredIssuer, "jwtSecret", jwtSecret);
                ReflectionTestUtils.setField(expiredIssuer, "jwtExpirationMs", -1_000L);
                String expired = expiredIssuer.generateToken("user@test.com", "USER");

                mockMvc.perform(get("/api/loan/history").header("Authorization", bearer(expired)))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("[TC-AUTH-107] 다른 비밀키로 서명한 토큰은 401")
        void otherSecretToken_returns401() throws Exception {
                JwtUtil otherIssuer = new JwtUtil();
                ReflectionTestUtils.setField(otherIssuer, "jwtSecret",
                                "Another-Secret-Key-That-Is-Long-Enough-For-HS256!!");
                ReflectionTestUtils.setField(otherIssuer, "jwtExpirationMs", 3_600_000L);
                String forged = otherIssuer.generateToken("admin@test.com", "ADMIN");

                mockMvc.perform(get("/api/admin/audit-logs").header("Authorization", bearer(forged)))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("[TC-AUTH-108] Bearer 접두사 없는 Authorization 헤더는 401")
        void tokenWithoutBearerPrefix_returns401() throws Exception {
                String token = api.signupAndLogin(ApiTestSupport.uniqueEmail("prefix"));

                mockMvc.perform(get("/api/loan/history").header("Authorization", token))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("[TC-AUTH-109] 정상 토큰이면 보호된 API 200")
        void validToken_returns200() throws Exception {
                String token = api.signupAndLogin(ApiTestSupport.uniqueEmail("valid"));

                mockMvc.perform(get("/api/loan/history").header("Authorization", bearer(token)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("[TC-AUTH-110] 응답 헤더 X-Trace-Id: 받은 값 유지, 없으면 hfm- 생성")
        void traceIdHeader_echoedOrGenerated() throws Exception {
                mockMvc.perform(get("/actuator/health").header("X-Trace-Id", "hfm-given-1"))
                                .andExpect(header().string("X-Trace-Id", "hfm-given-1"));
                mockMvc.perform(get("/actuator/health"))
                                .andExpect(header().string("X-Trace-Id", startsWith("hfm-")));
        }
}
