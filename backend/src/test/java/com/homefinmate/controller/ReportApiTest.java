package com.homefinmate.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homefinmate.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;

import static com.homefinmate.support.ApiTestSupport.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 리포트 API 통합 테스트 (응답 시각 형식)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportApiTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    // 응답 시각(생성 시각·계산 시각)에 시간대가 붙고, 실제 시각과 맞음
    @Test
    @DisplayName("[TC-RPT-101] 리포트 생성 시각과 계산 시각에 시간대가 붙어 내려가고 실제 시각과 1분 이내 (#91)")
    void reportTimes_includeOffset_andMatchNow() throws Exception {
        ApiTestSupport api = new ApiTestSupport(mockMvc, objectMapper);
        String token = api.signupAndLogin(ApiTestSupport.uniqueEmail("report-time"));
        mockMvc.perform(post("/api/loan/calculate").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"loanAmount\":100000000,\"annualInterestRate\":4,\"termMonths\":120,\"monthlyIncome\":4000000}"))
                .andExpect(status().isOk());

        JsonNode data = api.read(mockMvc.perform(get("/api/report/summary").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn()).path("data");

        for (String value : new String[] { data.path("generatedAt").asText(),
                data.path("reportData").path("lastSimulation").path("createdAt").asText() }) {
            Instant parsed = OffsetDateTime.parse(value).toInstant();
            assertThat(Duration.between(parsed, Instant.now()).abs()).isLessThan(Duration.ofMinutes(1));
        }
    }
}
