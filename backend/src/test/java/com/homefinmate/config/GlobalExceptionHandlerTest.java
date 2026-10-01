package com.homefinmate.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

        @GetMapping("/test/no-resource")
        String noResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "nothing");
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
    @DisplayName("없는 주소(NoResourceFoundException)")
    void noResource_returns404() throws Exception {
        mockMvc.perform(get("/test/no-resource"))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"status\":\"ERROR\",\"message\":\"Not Found\"}", true));
    }

    @Test
    @DisplayName("지원하지 않는 메서드(HttpRequestMethodNotSupportedException)")
    void wrongMethod_returns405() throws Exception {
        mockMvc.perform(post("/test/runtime"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().json("{\"status\":\"ERROR\",\"message\":\"Method Not Allowed\"}", true));
    }

    @Test
    @DisplayName("체크 예외(IOException)")
    void checkedException_returns500() throws Exception {
        mockMvc.perform(get("/test/checked"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json("{\"status\":\"ERROR\",\"message\":\"Internal server error\"}", true));
    }
}
