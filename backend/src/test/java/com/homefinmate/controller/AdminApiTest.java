package com.homefinmate.controller;

import com.homefinmate.repository.UserRepository;
import com.homefinmate.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homefinmate.entity.AuditLog;
import com.homefinmate.entity.LoginFailureLog;
import com.homefinmate.repository.LoginFailureLogRepository;
import com.homefinmate.util.EmailHasher;
import org.springframework.http.MediaType;
import com.homefinmate.repository.AuditLogRepository;
import com.homefinmate.security.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.homefinmate.support.ApiTestSupport.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 관리자 연동 실패 조회 API 통합 테스트
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminApiTest {

        @Autowired
        MockMvc mockMvc;
        @Autowired
        ObjectMapper objectMapper;
        @Autowired
        JwtUtil jwtUtil;
        @Autowired
        AuditLogRepository auditLogRepository;
        @Autowired
        UserRepository userRepository;
        @Autowired
        LoginFailureLogRepository loginFailureLogRepository;
        @Autowired
        EmailHasher emailHasher;

        @Test
        @DisplayName("[TC-DATA-103] 관리자 연동 실패 조회는 연동 실패 기록만, 100건 이하로 응답")
        void integrationFailures_returnsOnlyFailures() throws Exception {
                String marker = UUID.randomUUID().toString();
                auditLogRepository.save(AuditLog.builder().actorType("SYSTEM").actorId("HOMEFIT_MATE")
                                .action("RISKWATCH_INTEGRATION_FAILURE: " + marker).build());
                auditLogRepository.save(AuditLog.builder().actorType("USER").actorId("someone@test.com")
                                .action("LOGIN_SUCCESS").traceId(marker).build());

                String body = mockMvc.perform(get("/api/admin/integration-failures")
                                .header("Authorization", bearer(ApiTestSupport.adminToken(userRepository, jwtUtil))))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                List<String> actions = new ArrayList<>();
                for (JsonNode node : objectMapper.readTree(body).path("data")) {
                        actions.add(node.path("action").asText());
                }
                assertThat(actions).hasSizeLessThanOrEqualTo(100)
                                .allSatisfy(a -> assertThat(a).startsWith("RISKWATCH_INTEGRATION_FAILURE"))
                                .contains("RISKWATCH_INTEGRATION_FAILURE: " + marker);
        }

        // 관리자 이메일 검색: 그 이메일의 실패 기록만, 응답에 이메일 원문 없음, USER는 403
        @Test
        @DisplayName("[TC-DATA-104] 관리자가 이메일로 로그인 실패 검색: 그 이메일(대소문자·공백 무시)의 기록만, 응답에 이메일 원문 없음, USER 403·잘못된 이메일 400 (#89)")
        void searchLoginFailures_byEmail_returnsOnlyThatUser() throws Exception {
                String email = "search-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
                loginFailureLogRepository.save(LoginFailureLog.builder().emailHash(emailHasher.hash(email))
                                .reason("Incorrect password").build());
                loginFailureLogRepository.save(LoginFailureLog.builder().emailHash(emailHasher.hash(email))
                                .reason("Email not found").build());
                loginFailureLogRepository.save(LoginFailureLog.builder().emailHash(emailHasher.hash("other@test.com"))
                                .reason("Incorrect password").build());
                String adminToken = ApiTestSupport.adminToken(userRepository, jwtUtil);
                String searchBody = "{\"email\":\" " + email.toUpperCase() + " \"}";

                String body = mockMvc.perform(post("/api/admin/login-failures/search")
                                .header("Authorization", bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON).content(searchBody))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                List<String> hashes = new ArrayList<>();
                for (JsonNode node : objectMapper.readTree(body).path("data")) {
                        hashes.add(node.path("emailHash").asText());
                }
                assertThat(hashes).hasSize(2).containsOnly(emailHasher.hash(email));
                assertThat(body).doesNotContainIgnoringCase(email);

                String userToken = new ApiTestSupport(mockMvc, objectMapper)
                                .signupAndLogin(ApiTestSupport.uniqueEmail("not-admin"));
                mockMvc.perform(post("/api/admin/login-failures/search").header("Authorization", bearer(userToken))
                                .contentType(MediaType.APPLICATION_JSON).content(searchBody))
                                .andExpect(status().isForbidden());
                mockMvc.perform(post("/api/admin/login-failures/search").header("Authorization", bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"not-an-email\"}"))
                                .andExpect(status().isBadRequest());
        }
}
