package com.homefinmate.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.ArrayList;
import java.util.List;

import static com.homefinmate.support.ApiTestSupport.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 계산 이력 페이지 조회 통합 테스트 (#51)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoanHistoryApiTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    private ApiTestSupport api;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestSupport(mockMvc, objectMapper);
        token = api.signupAndLogin(ApiTestSupport.uniqueEmail("history"));
    }

    // 계산 n건 저장 (원금을 1억, 1억 1백만 … 으로 달리 해서 순서를 확인)
    private void calculate(int count) throws Exception {
        for (int i = 0; i < count; i++) {
            mockMvc.perform(post("/api/loan/calculate").header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"loanAmount\":" + (100000000 + i * 1000000)
                            + ",\"annualInterestRate\":4.0,\"termMonths\":120,\"monthlyIncome\":4000000}"))
                    .andExpect(status().isOk());
        }
    }

    private List<Long> ids(String query) throws Exception {
        JsonNode data = api.read(mockMvc.perform(get("/api/loan/history" + query)
                .header("Authorization", bearer(token))).andExpect(status().isOk()).andReturn()).get("data");
        List<Long> ids = new ArrayList<>();
        data.forEach(n -> ids.add(n.get("simulationId").asLong()));
        return ids;
    }

    @Test
    @DisplayName("[TC-LOAN-110] 이력은 최신순 페이지: 기본 20건, size·page로 나눠 조회, 페이지끼리 겹치지 않음, size 100이면 전체(21건) (#51)")
    void history_isPaged() throws Exception {
        calculate(21);

        List<Long> all = ids("?size=100");
        assertThat(all).hasSize(21).isSortedAccordingTo((a, b) -> Long.compare(b, a));
        assertThat(ids("")).hasSize(20).containsExactlyElementsOf(all.subList(0, 20));
        assertThat(ids("?page=0&size=2")).containsExactlyElementsOf(all.subList(0, 2));
        assertThat(ids("?page=1&size=2")).containsExactlyElementsOf(all.subList(2, 4));
        assertThat(ids("?page=10&size=2")).containsExactly(all.get(20));
        assertThat(ids("?page=11&size=2")).isEmpty();
    }

    @Test
    @DisplayName("[TC-LOAN-111] 이력 page·size 경계: size 0·101, page -1 → 400 안내, 숫자가 아님 → 400 Invalid parameter (500 아님)")
    void history_invalidPaging_returns400() throws Exception {
        for (String query : new String[] { "?size=0", "?size=101", "?page=-1" }) {
            mockMvc.perform(get("/api/loan/history" + query).header("Authorization", bearer(token)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("size must be between 1 and 100")));
        }
        mockMvc.perform(get("/api/loan/history?size=abc").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid parameter: size"));
    }
}
