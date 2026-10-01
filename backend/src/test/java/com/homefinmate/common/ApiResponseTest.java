package com.homefinmate.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// ApiResponse의 JSON 변환 결과 검증
class ApiResponseTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("success() test")
    void success_containsDataAndTraceId() throws Exception {
        // given(준비)
        Map<String, Integer> data = Map.of("monthlyPayment", 838723);

        // when(실행)
        String json = objectMapper.writeValueAsString(ApiResponse.success(data, "hfm-test-1"));

        // then(검증)
        assertThat(json).isEqualTo(
                "{\"status\":\"SUCCESS\",\"message\":\"OK\",\"data\":{\"monthlyPayment\":838723},\"traceId\":\"hfm-test-1\"}");
    }

    @Test
    @DisplayName("error() not null test")
    void error_omitsNullFields() throws Exception {
        String json = objectMapper.writeValueAsString(ApiResponse.error("Loan amount is required", null));

        assertThat(json).isEqualTo("{\"status\":\"ERROR\",\"message\":\"Loan amount is required\"}");
        assertThat(json).doesNotContain("data").doesNotContain("traceId");
    }
}
