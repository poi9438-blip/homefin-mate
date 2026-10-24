package com.homefinmate.support;

import com.homefinmate.entity.User;
import com.homefinmate.repository.UserRepository;
import com.homefinmate.security.JwtUtil;
import com.homefinmate.security.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// HTTP 통합 테스트 공통 도우미 (가입·로그인으로 토큰 발급, JSON 변환)
public final class ApiTestSupport {

    public static final String TEST_PASSWORD = "password123";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public ApiTestSupport(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    // 겹치지 않는 테스트용 이메일
    public static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
    }

    // 가입 후 로그인해서 토큰 반환
    public String signupAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", TEST_PASSWORD))));
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", TEST_PASSWORD))))
                .andReturn();
        return read(login).path("data").path("token").asText();
    }

    // DB에 ADMIN 사용자를 저장하고 그 사용자의 토큰 반환
    public static String adminToken(UserRepository userRepository, JwtUtil jwtUtil) {
        String email = uniqueEmail("admin");
        userRepository.save(User.builder().email(email).password("not-used").role(Role.ADMIN).build());
        return jwtUtil.generateToken(email, "ADMIN");
    }

    public String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    public JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }
}
