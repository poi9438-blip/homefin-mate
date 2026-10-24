package com.homefinmate.config;

import org.slf4j.MDC;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import com.homefinmate.common.BusinessException;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
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
@ExtendWith(OutputCaptureExtension.class)
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

        @GetMapping("/test/business")
        String business() {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Email already registered");
        }

        @GetMapping("/test/denied")
        String denied() {
            throw new AccessDeniedException("Access Denied");
        }

        @PostMapping("/test/body")
        String body(@RequestBody Map<String, Object> body) {
            return "ok";
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

    // 업무 오류(BusinessException)는 지정한 상태 코드 + 공개 메시지
    @Test
    @DisplayName("[TC-ERR-003] 업무 오류(BusinessException)는 지정한 상태 코드 + 메시지 그대로")
    void businessException_returnsItsStatusAndMessage() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"status\":\"ERROR\",\"message\":\"Email already registered\"}", true));
    }

    // 예상 못 한 예외는 500 + 일반 메시지 (SQL문·내부 메시지를 응답에 담지 않음)
    @Test
    @DisplayName("[TC-ERR-004] 예상 못 한 예외(SQL문이 담긴 메시지 포함)는 500 + 일반 메시지, 원래 메시지 숨김 (#4 해결)")
    void unexpectedException_hidesMessage() throws Exception {
        mockMvc.perform(get("/test/sql-like"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json("{\"status\":\"ERROR\",\"message\":\"Internal server error\"}", true));
        mockMvc.perform(get("/test/runtime"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Transaction amount"))));
    }

    // 응답에 숨긴 예외 종류·메시지가 서버 로그(ERROR)에 남음
    @Test
    @DisplayName("[TC-ERR-008] 예상 못 한 예외는 서버 로그에 예외 종류·메시지가 ERROR로 남음 (#46 해결)")
    void unexpectedException_isLogged(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/sql-like"));

        assertThat(output.getOut())
                .contains("ERROR")
                .contains("Unhandled exception: java.lang.IllegalStateException: could not execute statement");
    }

    // @PreAuthorize 거부(AccessDeniedException)는 403
    @Test
    @DisplayName("[TC-ERR-009] 권한 거부(AccessDeniedException)는 403 Forbidden (#84 해결)")
    void accessDenied_returns403() throws Exception {
        mockMvc.perform(get("/test/denied"))
                .andExpect(status().isForbidden())
                .andExpect(content().json("{\"status\":\"ERROR\",\"message\":\"Forbidden\"}", true));
    }

    // 깨진 JSON은 400 + 일반 메시지 (파서 내부 메시지 숨김)
    @Test
    @DisplayName("[TC-ERR-010] 깨진 JSON 본문은 400 Malformed request body, 파서 메시지 숨김")
    void malformedJson_returns400WithoutParserDetails() throws Exception {
        mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{\"a\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"status\":\"ERROR\",\"message\":\"Malformed request body\"}", true));
    }

    // 요청의 traceId(MDC)가 오류 응답 본문에도 들어감
    @Test
    @DisplayName("[TC-ERR-011] 오류 응답 본문에 요청의 traceId(MDC)가 들어감")
    void errorBody_containsTraceIdFromMdc() throws Exception {
        MDC.put("traceId", "hfm-err-trace-1");
        try {
            mockMvc.perform(get("/test/business"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.traceId").value("hfm-err-trace-1"));
            mockMvc.perform(get("/test/sql-like"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.traceId").value("hfm-err-trace-1"));
        } finally {
            MDC.remove("traceId");
        }
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
