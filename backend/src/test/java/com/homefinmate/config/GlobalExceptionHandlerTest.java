package com.homefinmate.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 예외 종류별로 HTTP 상태 코드와 응답 형식 검증
class GlobalExceptionHandlerTest {

    @RestController
    static class ThrowingController {
        @GetMapping("/test/runtime")
        String runtime() {
            throw new IllegalStateException("Transaction amount must be positive");
        }

        @GetMapping("/test/sql-like")
        String sqlLike() {
            throw new IllegalStateException("could not execute statement [insert into loan_simulations (...)]");
        }

        @GetMapping("/test/checked")
        String checked() throws IOException {
            throw new IOException("disk read failed");
        }
    }

    private MockMvc mockMvc;

    // 테스트끼리 영향 없게 매번 새로 준비
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("RuntimeException")
    void runtimeException_returns400() throws Exception {
        mockMvc.perform(get("/test/runtime"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"status\":\"ERROR\",\"message\":\"Transaction amount must be positive\"}",
                        true));
    }

    // TODO: 상세 내용 제거 예정
    @Test
    @DisplayName("RuntimeException")
    void runtimeException_exposesMessage() throws Exception {
        mockMvc.perform(get("/test/sql-like"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("insert into loan_simulations")));
    }

    @Test
    @DisplayName("체크 예외(IOException)")
    void checkedException_returns500() throws Exception {
        mockMvc.perform(get("/test/checked"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json("{\"status\":\"ERROR\",\"message\":\"Internal server error\"}", true));
    }
}
