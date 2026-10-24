package com.homefinmate.controller;

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
import org.springframework.test.web.servlet.ResultActions;

import static com.homefinmate.support.ApiTestSupport.bearer;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 대출 계산·DTI API 입력 경계 통합 테스트
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoanApiValidationTest {

        @Autowired
        MockMvc mockMvc;
        @Autowired
        ObjectMapper objectMapper;

        private String token;

        @BeforeEach
        void setUp() throws Exception {
                token = new ApiTestSupport(mockMvc, objectMapper)
                                .signupAndLogin(ApiTestSupport.uniqueEmail("loan-input"));
        }

        private ResultActions postJson(String path, String body) throws Exception {
                return mockMvc.perform(post(path).header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON).content(body));
        }

        @Test
        @DisplayName("[TC-LOAN-101] 필수값(대출 원금)이 없으면 400과 필드 이름")
        void missingRequiredField_returns400WithFieldName() throws Exception {
                postJson("/api/loan/calculate", "{\"annualInterestRate\":4.5,\"termMonths\":360}")
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(containsString("loanAmount")));
        }

        @Test
        @DisplayName("[TC-LOAN-102] 기간 601개월·음수 원금·음수 연이율은 대출 계산·통합 분석 모두 400 + 필드 이름 (#82 해결)")
        void upperBoundAndSignValidation_rejected() throws Exception {
                postJson("/api/loan/calculate",
                                "{\"loanAmount\":300000000,\"annualInterestRate\":4.5,\"termMonths\":601}")
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(containsString("termMonths")));
                postJson("/api/loan/calculate",
                                "{\"loanAmount\":-1000000,\"annualInterestRate\":4.5,\"termMonths\":12}")
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(containsString("loanAmount")));
                postJson("/api/loan/calculate",
                                "{\"loanAmount\":1000000,\"annualInterestRate\":-1,\"termMonths\":12}")
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(containsString("annualInterestRate")));

                // 통합 분석의 필수 입력은 모두 넣어서, 400이 기간 601개월 때문인지 확인
                postJson("/api/analyze", "{\"loanAmount\":300000000,\"annualInterestRate\":4.5,\"termMonths\":601,"
                                + "\"monthlyIncome\":4000000,\"housePrice\":400000000,\"householdAnnualIncome\":48000000,"
                                + "\"firstHome\":true,\"newlywed\":false,\"childCount\":0}")
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(containsString("termMonths")));
        }

        @Test
        // 기간 0개월은 계산 전에 검증에서 막음 (예전에는 계산하다 "/ by zero" 예외가 응답에 나감)
        @DisplayName("[TC-LOAN-103] 기간 0개월이면 400 + 검증 메시지 (\"/ by zero\" 아님, #6 해결)")
        void zeroTerm_rejectedByValidation() throws Exception {
                postJson("/api/loan/calculate",
                                "{\"loanAmount\":300000000,\"annualInterestRate\":4.5,\"termMonths\":0}")
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value("termMonths: Term must be at least 1 month"));
        }

        @Test
        // 연이율 10%도 계산·저장됨 (interest_rate DECIMAL(6,4))
        @DisplayName("[TC-LOAN-104] 연이율 10%도 200으로 계산·저장")
        void interestRateTenPercent_isSaved() throws Exception {
                postJson("/api/loan/calculate",
                                "{\"loanAmount\":300000000,\"annualInterestRate\":10,\"termMonths\":360}")
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.simulationId").isNumber());
        }

        @Test
        // 월 상환 1,520,055.93 ÷ 소득 10만 = DTI 15.2006 (1520%)도 저장되고 HIGH (dti
        // DECIMAL(7,4))
        @DisplayName("[TC-LOAN-105] 소득 10만(DTI 1520%)도 200으로 저장, 판정 HIGH (#74 해결)")
        void largeDti_isSavedAsHigh() throws Exception {
                postJson("/api/loan/calculate",
                                "{\"loanAmount\":300000000,\"annualInterestRate\":4.5,\"termMonths\":360,\"monthlyIncome\":100000}")
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.dti").value(15.2006))
                                .andExpect(jsonPath("$.data.dtiStatus").value("HIGH"))
                                .andExpect(jsonPath("$.data.simulationId").isNumber());
        }

        // 컬럼에 못 들어가는 연이율(100%)은 저장까지 가지 않고 요청 검증에서 400 + 필드 이름 (SQL 노출 없음)
        @Test
        @DisplayName("[TC-LOAN-107] 연이율 100%는 대출 계산·통합 분석 모두 400 + annualInterestRate (저장 시도 없음)")
        void interestRateOver99_returns400() throws Exception {
                postJson("/api/loan/calculate",
                                "{\"loanAmount\":300000000,\"annualInterestRate\":100,\"termMonths\":360}")
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(containsString("annualInterestRate")));
                postJson("/api/analyze", "{\"loanAmount\":300000000,\"annualInterestRate\":100,\"termMonths\":360,"
                                + "\"monthlyIncome\":4000000,\"housePrice\":400000000,\"householdAnnualIncome\":48000000,"
                                + "\"firstHome\":true,\"newlywed\":false,\"childCount\":0}")
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(containsString("annualInterestRate")));
        }

        @Test
        // 월 상환액이 없으면 검증 메시지 (예전에는 NPE 메시지가 응답에 나감)
        @DisplayName("[TC-LOAN-106] DTI API에 월 상환액이 없으면 400 + 검증 메시지, 내부 메시지 노출 없음 (#6 해결)")
        void dtiMissingPayment_rejectedByValidation() throws Exception {
                postJson("/api/loan/dti", "{\"monthlyIncome\":1000000}")
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value("monthlyPayment: Monthly payment is required"));
        }

        // 빈 요청, 빈 소득 0은 판정하지 않고 400
        @Test
        @DisplayName("[TC-LOAN-108] DTI API에 빈 요청·소득 0이면 400 (예전에는 DTI 0 적정, #64 일부 해결)")
        void dtiEmptyOrZeroIncome_rejected() throws Exception {
                postJson("/api/loan/dti", "{}")
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(containsString("monthlyPayment")))
                                .andExpect(jsonPath("$.message").value(containsString("monthlyIncome")));
                postJson("/api/loan/dti", "{\"monthlyPayment\":1000000,\"monthlyIncome\":0}")
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message")
                                                .value("monthlyIncome: Monthly income must be positive"));
        }

        // 오류 응답에서도 헤더와 본문의 traceId가 같음 (사용자가 본 번호로 서버 로그 검색 가능)
        @Test
        @DisplayName("[TC-ERR-012] 실제 요청의 오류 응답: 보낸 X-Trace-Id가 응답 헤더와 본문 traceId에 같은 값으로 들어감")
        void errorResponse_echoesTraceIdInHeaderAndBody() throws Exception {
                mockMvc.perform(post("/api/loan/calculate").header("Authorization", bearer(token))
                                .header("X-Trace-Id", "hfm-err-trace-2")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"loanAmount\":300000000,\"annualInterestRate\":4.5,\"termMonths\":0}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(header().string("X-Trace-Id", "hfm-err-trace-2"))
                                .andExpect(jsonPath("$.traceId").value("hfm-err-trace-2"));
        }
}
