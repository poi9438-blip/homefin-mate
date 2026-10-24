package com.homefinmate.security;

import com.homefinmate.entity.User;
import com.homefinmate.repository.UserRepository;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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
        @Autowired
        UserRepository userRepository;

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
        @DisplayName("[TC-AUTH-102] 공개 API(DTI, 정책 추천, health)는 토큰 없이 200")
        void publicApis_withoutToken_areAllowed() throws Exception {
                mockMvc.perform(post("/api/loan/dti").contentType(MediaType.APPLICATION_JSON)
                                .content(api.json(Map.of("monthlyPayment", 380000, "monthlyIncome", 1000000))))
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
                String adminToken = ApiTestSupport.adminToken(userRepository, jwtUtil);

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

        // 비밀번호 틀림·없는 이메일 모두 401 + 같은 메시지 (계정 존재 여부를 응답으로 구분할 수 없음)
        @Test
        @DisplayName("[TC-AUTH-111] 로그인 실패는 비밀번호 틀림·없는 이메일 모두 401 + 같은 메시지 (#88)")
        void loginFailure_returns401WithSameMessage() throws Exception {
                String email = ApiTestSupport.uniqueEmail("login-401");
                api.signupAndLogin(email);

                mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                                .content(api.json(Map.of("email", email, "password", "wrong-pass"))))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.message").value("Invalid credentials"));
                mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                                .content(api.json(Map.of("email", "nobody-" + email, "password", "wrong-pass"))))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.message").value("Invalid credentials"));
        }

        // 실제 HTTP: 5번 틀리면 6번째는 비밀번호가 맞아도 429, 다른 이메일은 영향 없음
        @Test
        @DisplayName("[TC-AUTH-112] 같은 이메일로 5번 틀리면 맞는 비밀번호도 429, 다른 이메일은 정상 로그인 (#79)")
        void fiveFailures_thenLocked_otherEmailUnaffected() throws Exception {
                String email = ApiTestSupport.uniqueEmail("lock");
                String other = ApiTestSupport.uniqueEmail("lock-other");
                api.signupAndLogin(email);
                api.signupAndLogin(other);
                for (int i = 0; i < 5; i++) {
                        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                                        .content(api.json(Map.of("email", email, "password", "wrong-pass"))))
                                        .andExpect(status().isUnauthorized());
                }

                mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                                .content(api.json(Map.of("email", email, "password", ApiTestSupport.TEST_PASSWORD))))
                                .andExpect(status().isTooManyRequests())
                                .andExpect(jsonPath("$.message").value("Too many login attempts. Try again later"));
                mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                                .content(api.json(Map.of("email", other, "password", ApiTestSupport.TEST_PASSWORD))))
                                .andExpect(status().isOk());
        }

        // 역할 없는 토큰: DB의 USER 권한으로 인증(사용자 API 200, 관리자 API 403), 빈 "ROLE_" 권한 없음
        @Test
        @DisplayName("[TC-AUTH-113] 역할이 없는 토큰도 권한은 DB 기준: 사용자 API 200, 관리자 API 403 (#81)")
        void tokenWithoutRole_usesDbRole() throws Exception {
                String email = ApiTestSupport.uniqueEmail("no-role");
                api.signupAndLogin(email);
                String noRole = Jwts.builder()
                                .subject(email)
                                .issuedAt(new java.util.Date())
                                .expiration(new java.util.Date(System.currentTimeMillis() + 60_000))
                                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                                .compact();

                mockMvc.perform(get("/api/loan/history").header("Authorization", bearer(noRole)))
                                .andExpect(status().isOk());
                mockMvc.perform(get("/api/admin/audit-logs").header("Authorization", bearer(noRole)))
                                .andExpect(status().isForbidden());
        }

        // [학습] 예전에는 토큰의 역할을 믿어서, DB에서 ADMIN을 회수해도 같은 토큰으로 24시간 동안 관리자 API를 쓸 수 있었음
        // [추천 주석] DB에서 ADMIN → USER로 바꾸면 같은 토큰으로 다음 요청부터 관리자 API 403
        @Test
        @DisplayName("[TC-AUTH-114] DB에서 ADMIN 권한을 회수하면 기존 토큰으로 다음 요청부터 관리자 API 403 (#44)")
        void revokedAdmin_isDeniedImmediately() throws Exception {
                String adminToken = ApiTestSupport.adminToken(userRepository, jwtUtil);
                mockMvc.perform(get("/api/admin/audit-logs").header("Authorization", bearer(adminToken)))
                                .andExpect(status().isOk());

                User admin = userRepository.findByEmail(jwtUtil.extractEmail(adminToken)).orElseThrow();
                admin.setRole(Role.USER);
                userRepository.save(admin);

                mockMvc.perform(get("/api/admin/audit-logs").header("Authorization", bearer(adminToken)))
                                .andExpect(status().isForbidden());
        }

        // [추천 주석] 토큰은 유효해도 DB에서 사용자가 지워졌으면 인증하지 않음
        @Test
        @DisplayName("[TC-AUTH-115] 삭제된 사용자의 토큰은 보호된 API에서 401")
        void deletedUserToken_returns401() throws Exception {
                String email = ApiTestSupport.uniqueEmail("deleted");
                String token = api.signupAndLogin(email);
                userRepository.delete(userRepository.findByEmail(email).orElseThrow());

                mockMvc.perform(get("/api/loan/history").header("Authorization", bearer(token)))
                                .andExpect(status().isUnauthorized());
        }

        // [학습] 브라우저는 다른 출처로 요청하기 전에 OPTIONS(사전 요청)로 허용 여부를 물어봄 → 그 응답으로 확인
        // [추천 주석] 화면 주소(localhost:3100)는 허용, 다른 출처는 403 (예전에는 모든 출처 + credentials 허용)
        @Test
        @DisplayName("[TC-AUTH-116] CORS: 화면 주소는 허용, 다른 출처의 사전 요청은 403, credentials 허용 안 함 (#28)")
        void cors_allowsOnlyConfiguredOrigin() throws Exception {
                mockMvc.perform(options("/api/loan/calculate")
                                .header("Origin", "http://localhost:3100")
                                .header("Access-Control-Request-Method", "POST")
                                .header("Access-Control-Request-Headers", "authorization,content-type"))
                                .andExpect(status().isOk())
                                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3100"))
                                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
                mockMvc.perform(options("/api/loan/calculate")
                                .header("Origin", "https://evil.example")
                                .header("Access-Control-Request-Method", "POST"))
                                .andExpect(status().isForbidden())
                                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        }

        // [추천 주석] Swagger UI·API 문서는 기본 꺼짐 (SWAGGER_ENABLED=true일 때만)
        @Test
        @DisplayName("[TC-AUTH-117] Swagger UI·API 문서는 기본 설정에서 404 (#80)")
        void swagger_isDisabledByDefault() throws Exception {
                mockMvc.perform(get("/api-docs")).andExpect(status().isNotFound());
                mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
        }
}
