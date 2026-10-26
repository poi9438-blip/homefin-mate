package com.homefinmate.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homefinmate.security.AuthCookies;
import com.homefinmate.security.JwtUtil;
import com.homefinmate.support.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static com.homefinmate.support.ApiTestSupport.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// 로그인 쿠키(HttpOnly)와 CSRF 보호 통합 테스트 (#78)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CookieAuthTest {

        private static final String LOAN = "{\"loanAmount\":100000000,\"annualInterestRate\":4,\"termMonths\":120,"
                        + "\"monthlyIncome\":4000000}";

        @Autowired
        MockMvc mockMvc;
        @Autowired
        ObjectMapper objectMapper;
        @Autowired
        JwtUtil jwtUtil;

        private ApiTestSupport api;
        private String email;

        @BeforeEach
        void setUp() {
                api = new ApiTestSupport(mockMvc, objectMapper);
                email = ApiTestSupport.uniqueEmail("cookie");
        }

        private MockHttpServletResponse login() throws Exception {
                mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                                .content(api.json(Map.of("email", email, "password", ApiTestSupport.TEST_PASSWORD))));
                return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                                .content(api.json(Map.of("email", email, "password", ApiTestSupport.TEST_PASSWORD))))
                                .andReturn().getResponse();
        }

        private String setCookie(MockHttpServletResponse res, String name) {
                List<String> headers = res.getHeaders(HttpHeaders.SET_COOKIE);
                return headers.stream().filter(h -> h.startsWith(name + "=")).findFirst().orElseThrow();
        }

        @Test
        @DisplayName("[TC-AUTH-120] 로그인 성공: 토큰은 HttpOnly·Secure·SameSite=Strict·Path=/api 쿠키로만, 본문에는 token 없이 expiresAt(토큰 exp와 같음)")
        void login_setsHttpOnlyCookie_andBodyHasNoToken() throws Exception {
                MockHttpServletResponse res = login();

                String cookie = setCookie(res, AuthCookies.SESSION);
                assertThat(cookie).contains("HttpOnly").contains("Secure").contains("SameSite=Strict")
                                .contains("Path=/api")
                                .contains("Max-Age=");
                JsonNode data = objectMapper.readTree(res.getContentAsString()).path("data");
                assertThat(data.has("token")).isFalse();
                String token = res.getCookie(AuthCookies.SESSION).getValue();
                assertThat(data.path("expiresAt").asLong()).isEqualTo(jwtUtil.expiresAtMillis(token));
                assertThat(res.getContentAsString()).doesNotContain(token);
        }

        // 쿠키만으로 조회 API 인증, 쿠키가 없으면 401
        @Test
        @DisplayName("[TC-AUTH-121] Authorization 헤더 없이 hfm_session 쿠키만으로 GET 인증 200, 쿠키가 없으면 401")
        void cookieOnly_authenticatesGet() throws Exception {
                Cookie session = login().getCookie(AuthCookies.SESSION);

                assertThat(mockMvc.perform(get("/api/loan/history").cookie(session)).andReturn().getResponse()
                                .getStatus())
                                .isEqualTo(200);
                assertThat(mockMvc.perform(get("/api/loan/history")).andReturn().getResponse().getStatus())
                                .isEqualTo(401);
        }

        // 서버가 준 XSRF-TOKEN 값을 헤더로 함께 보내야만 허용. 다른 사이트는 이 값을 읽을 수 없음
        @Test
        @DisplayName("[TC-AUTH-122] 쿠키 인증 POST: XSRF 헤더 없으면 403, XSRF-TOKEN 쿠키와 같은 X-XSRF-TOKEN 헤더면 200, 값이 다르면 403")
        void cookiePost_requiresCsrfToken() throws Exception {
                Cookie session = login().getCookie(AuthCookies.SESSION);
                Cookie xsrf = mockMvc.perform(get("/api/loan/history").cookie(session)).andReturn().getResponse()
                                .getCookie("XSRF-TOKEN");
                assertThat(xsrf).isNotNull();

                assertThat(mockMvc.perform(post("/api/loan/calculate").cookie(session, xsrf)
                                .contentType(MediaType.APPLICATION_JSON).content(LOAN)).andReturn().getResponse()
                                .getStatus())
                                .isEqualTo(403);
                assertThat(mockMvc
                                .perform(post("/api/loan/calculate").cookie(session, xsrf)
                                                .header("X-XSRF-TOKEN", "forged")
                                                .contentType(MediaType.APPLICATION_JSON).content(LOAN))
                                .andReturn().getResponse().getStatus())
                                .isEqualTo(403);
                assertThat(mockMvc.perform(post("/api/loan/calculate").cookie(session, xsrf)
                                .header("X-XSRF-TOKEN", xsrf.getValue())
                                .contentType(MediaType.APPLICATION_JSON).content(LOAN)).andReturn().getResponse()
                                .getStatus())
                                .isEqualTo(200);
        }

        @Test
        @DisplayName("[TC-AUTH-123] Authorization: Bearer로 인증한 POST는 XSRF 헤더 없이 200 (CSRF 확인 대상 아님)")
        void bearerPost_skipsCsrf() throws Exception {
                String token = login().getCookie(AuthCookies.SESSION).getValue();

                assertThat(mockMvc.perform(post("/api/loan/calculate").header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON).content(LOAN)).andReturn().getResponse()
                                .getStatus())
                                .isEqualTo(200);
        }

        @Test
        @DisplayName("[TC-AUTH-124] 로그아웃은 같은 이름·경로의 hfm_session 쿠키를 Max-Age=0으로 지움 (XSRF 헤더 필요)")
        void logout_clearsCookie() throws Exception {
                Cookie session = login().getCookie(AuthCookies.SESSION);
                Cookie xsrf = mockMvc.perform(get("/api/auth/me").cookie(session)).andReturn().getResponse()
                                .getCookie("XSRF-TOKEN");

                MockHttpServletResponse res = mockMvc.perform(post("/api/auth/logout").cookie(session, xsrf)
                                .header("X-XSRF-TOKEN", xsrf.getValue())).andReturn().getResponse();

                assertThat(res.getStatus()).isEqualTo(200);
                assertThat(setCookie(res, AuthCookies.SESSION)).contains("Max-Age=0").contains("Path=/api");
        }

        // XSRF-TOKEN 쿠키는 화면이 읽어야 해서 HttpOnly 아님, 모든 경로(Path=/)
        @Test
        @DisplayName("[TC-AUTH-125] 응답에 XSRF-TOKEN 쿠키가 내려가고 HttpOnly가 아니며 Path=/, SameSite=Strict")
        void xsrfCookie_isReadableByScript() throws Exception {
                Cookie xsrf = mockMvc.perform(get("/actuator/health")).andReturn().getResponse()
                                .getCookie("XSRF-TOKEN");

                assertThat(xsrf).isNotNull();
                assertThat(xsrf.isHttpOnly()).isFalse();
                assertThat(xsrf.getPath()).isEqualTo("/");
                assertThat(xsrf.getAttribute("SameSite")).isEqualTo("Strict");
        }

        // 브라우저 흐름(가입·로그인 → 조회 → 계산)에서 XSRF-TOKEN이 지워지지 않고 계산 POST 성공
        @Test
        @DisplayName("[TC-AUTH-126] 브라우저 흐름(가입·로그인 → 쿠키 인증 조회 → 계산 POST): 조회 응답이 XSRF-TOKEN을 지우지 않고, 처음 받은 XSRF 값으로 POST 200")
        void browserFlow_keepsXsrfToken() throws Exception {
                MockHttpServletResponse signup = mockMvc
                                .perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                                                .content(api.json(Map.of("email", email, "password",
                                                                ApiTestSupport.TEST_PASSWORD))))
                                .andReturn().getResponse();
                Cookie xsrf = signup.getCookie("XSRF-TOKEN");
                assertThat(xsrf).isNotNull();
                Cookie session = mockMvc
                                .perform(post("/api/auth/login").cookie(xsrf).contentType(MediaType.APPLICATION_JSON)
                                                .content(api.json(Map.of("email", email, "password",
                                                                ApiTestSupport.TEST_PASSWORD))))
                                .andReturn().getResponse().getCookie(AuthCookies.SESSION);

                MockHttpServletResponse history = mockMvc.perform(get("/api/loan/history").cookie(session, xsrf))
                                .andReturn().getResponse();

                assertThat(history.getStatus()).isEqualTo(200);
                assertThat(history.getHeaders(HttpHeaders.SET_COOKIE)).noneMatch(h -> h.startsWith("XSRF-TOKEN="));
                assertThat(mockMvc
                                .perform(post("/api/loan/calculate").cookie(session, xsrf)
                                                .header("X-XSRF-TOKEN", xsrf.getValue())
                                                .contentType(MediaType.APPLICATION_JSON).content(LOAN))
                                .andReturn().getResponse().getStatus())
                                .isEqualTo(200);
        }
}
