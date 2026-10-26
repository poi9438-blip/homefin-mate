package com.homefinmate.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homefinmate.entity.User;
import com.homefinmate.repository.UserRepository;
import com.homefinmate.security.AdminAccountInitializer;
import com.homefinmate.security.Role;
import com.homefinmate.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static com.homefinmate.support.ApiTestSupport.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 인증 API 통합 테스트 (가입 검증 메시지, 현재 사용자, 관리자 지정)
@SpringBootTest(properties = "app.admin.emails=promote-me@test.com, signup-admin@test.com")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    UserRepository userRepository;
    @Autowired
    AdminAccountInitializer adminAccountInitializer;

    @Test
    @DisplayName("[TC-AUTH-302] 회원가입 입력 검증: 이메일 형식·비밀번호 8자 미만이면 400 + 영어 필드 메시지 (한국어 기본 문구 없음, #75)")
    void signupValidation_englishMessages() throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("email: Email must be valid")))
                .andExpect(
                        jsonPath("$.message").value(containsString("password: Password must be at least 8 characters")))
                .andExpect(jsonPath("$.message").value(not(containsString("니다"))));
    }

    // 현재 사용자: 일반 가입은 USER, ADMIN_EMAILS로 가입하면 ADMIN (역할은 DB 기준)
    @Test
    @DisplayName("[TC-AUTH-118] GET /api/auth/me는 로그인 사용자의 이메일·역할(DB 기준): 일반 가입 USER, ADMIN_EMAILS 가입 ADMIN (#10)")
    void me_returnsEmailAndRole() throws Exception {
        ApiTestSupport api = new ApiTestSupport(mockMvc, objectMapper);
        String userEmail = ApiTestSupport.uniqueEmail("me");
        String userToken = api.signupAndLogin(userEmail);
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(userEmail))
                .andExpect(jsonPath("$.data.role").value("USER"));

        String adminToken = api.signupAndLogin("Signup-Admin@test.com");
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    @DisplayName("[TC-AUTH-119] 서버 시작 시 ADMIN_EMAILS에 있는 기존 사용자는 ADMIN으로 바뀌고, 목록에 없는 사용자는 그대로 (#10)")
    void initializer_promotesListedExistingUsers() throws Exception {
        User listed = userRepository
                .save(User.builder().email("promote-me@test.com").password("x").role(Role.USER).build());
        User other = userRepository.save(User.builder().email(ApiTestSupport.uniqueEmail("stay")).password("x")
                .role(Role.USER).build());

        adminAccountInitializer.run(null);

        assertThat(userRepository.findById(listed.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
        assertThat(userRepository.findById(other.getId()).orElseThrow().getRole()).isEqualTo(Role.USER);
    }
}
