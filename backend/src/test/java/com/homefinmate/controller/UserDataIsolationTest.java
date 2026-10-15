package com.homefinmate.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homefinmate.repository.LoanSimulationRepository;
import com.homefinmate.repository.UserRepository;
import com.homefinmate.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static com.homefinmate.support.ApiTestSupport.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 사용자 간 데이터 분리 통합 테스트 (계산 이력, 리포트)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserDataIsolationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    UserRepository userRepository;
    @Autowired
    LoanSimulationRepository loanSimulationRepository;

    private ApiTestSupport api;
    private String emailA;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestSupport(mockMvc, objectMapper);
        emailA = ApiTestSupport.uniqueEmail("owner-a");
        tokenA = api.signupAndLogin(emailA);
        tokenB = api.signupAndLogin(ApiTestSupport.uniqueEmail("other-b"));
    }

    private Map<String, Object> loanBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("loanAmount", 300_000_000);
        body.put("annualInterestRate", 4.5);
        body.put("termMonths", 360);
        body.put("monthlyIncome", 4_000_000);
        return body;
    }

    private JsonNode getJson(String path, String token) throws Exception {
        return api.read(mockMvc.perform(get(path).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn());
    }

    @Test
    @DisplayName("[TC-DATA-101] A가 저장한 계산은 A의 이력·리포트에만 보이고 B에게는 보이지 않음")
    void historyAndReport_areScopedToTokenOwner() throws Exception {
        mockMvc.perform(post("/api/loan/calculate").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON).content(api.json(loanBody())))
                .andExpect(status().isOk());

        assertThat(getJson("/api/loan/history", tokenA).path("data")).hasSize(1);
        assertThat(getJson("/api/report/summary", tokenA).path("data").path("reportData")
                .path("totalSimulations").asInt()).isEqualTo(1);

        assertThat(getJson("/api/loan/history", tokenB).path("data")).isEmpty();
        assertThat(getJson("/api/report/summary", tokenB).path("data").path("reportData")
                .path("totalSimulations").asInt()).isZero();
    }

    @Test
    @DisplayName("[TC-DATA-102] 본문에 다른 사용자 userId를 넣어도 토큰 주인으로 저장")
    void bodyUserId_isIgnored_savedAsTokenOwner() throws Exception {
        Long idA = userRepository.findByEmail(emailA).orElseThrow().getId();
        Map<String, Object> body = loanBody();
        body.put("userId", idA);

        mockMvc.perform(post("/api/loan/calculate").header("Authorization", bearer(tokenB))
                .contentType(MediaType.APPLICATION_JSON).content(api.json(body)))
                .andExpect(status().isOk());

        assertThat(loanSimulationRepository.findByUserIdOrderByCreatedAtDesc(idA)).isEmpty();
        assertThat(getJson("/api/loan/history", tokenB).path("data")).hasSize(1);
    }
}
