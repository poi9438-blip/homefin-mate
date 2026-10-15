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
    @DisplayName("[TC-ERR-003] RuntimeException은 400 + 공통 오류 형식")
    void runtimeException_returns400() throws Exception {
        mockMvc.perform(get("/test/runtime"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"status\":\"ERROR\",\"message\":\"Transaction amount must be positive\"}",
                        true));
    }

    // TODO: 상세 내용 제거 예정
    @Test
    @DisplayName("[TC-ERR-004] ⚠️#4 예외 메시지(SQL문 등)가 응답에 그대로 노출")
    void runtimeException_exposesMessage() throws Exception {
        mockMvc.perform(get("/test/sql-like"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("insert into loan_simulations")));
    }

    @Test
    @DisplayName("[TC-ERR-005] 없는 주소는 404")
    void noResource_returns404() throws Exception {
        mockMvc.perform(get("/test/no-resource"))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"status\":\"ERROR\",\"message\":\"Not Found\"}", true));
    }

    @Test
    @DisplayName("[TC-ERR-006] 지원하지 않는 메서드는 405")
    void wrongMethod_returns405() throws Exception {
        mockMvc.perform(post("/test/runtime"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().json("{\"status\":\"ERROR\",\"message\":\"Method Not Allowed\"}", true));
    }

    @Test
    @DisplayName("[TC-ERR-007] 체크 예외는 500 + 내부 메시지 숨김")
    void checkedException_returns500() throws Exception {
        mockMvc.perform(get("/test/checked"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json("{\"status\":\"ERROR\",\"message\":\"Internal server error\"}", true));
    }
}
