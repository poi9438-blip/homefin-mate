package com.homefinmate.controller;

import com.homefinmate.repository.UserRepository;
import com.homefinmate.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homefinmate.entity.AuditLog;
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
}
