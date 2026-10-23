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
        token = new ApiTestSupport(mockMvc, objectMapper).signupAndLogin(ApiTestSupport.uniqueEmail("loan-input"));
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
    @DisplayName("[TC-LOAN-102] ⚠️#82 기간 601개월·음수 원금도 200으로 계산·저장됨 (통합 분석은 400)")
    void noUpperBoundOrSignValidation_currentlyAccepted() throws Exception {
        postJson("/api/loan/calculate", "{\"loanAmount\":300000000,\"annualInterestRate\":4.5,\"termMonths\":601}")
                .andExpect(status().isOk());
        postJson("/api/loan/calculate", "{\"loanAmount\":-1000000,\"annualInterestRate\":4.5,\"termMonths\":12}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.monthlyPayment").value(-85378.52));

        // 통합 분석의 필수 입력은 모두 넣어서, 400이 기간 601개월 때문인지 확인
        postJson("/api/analyze", "{\"loanAmount\":300000000,\"annualInterestRate\":4.5,\"termMonths\":601,"
                + "\"monthlyIncome\":4000000,\"housePrice\":400000000,\"householdAnnualIncome\":48000000,"
                + "\"firstHome\":true,\"newlywed\":false,\"childCount\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("termMonths")));
    }

    @Test
    @DisplayName("[TC-LOAN-103] ⚠️#6 기간 0개월이면 400 \"/ by zero\" (검증 대신 계산 예외)")
    void zeroTerm_currentlyDivisionByZero() throws Exception {
        postJson("/api/loan/calculate", "{\"loanAmount\":300000000,\"annualInterestRate\":4.5,\"termMonths\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("/ by zero"));
    }

    @Test
    @DisplayName("[TC-LOAN-104] ⚠️#1#4 연이율 10%는 저장 실패, 응답에 SQL문 노출")
    void interestRateTenPercent_saveFails_andLeaksSql() throws Exception {
        postJson("/api/loan/calculate", "{\"loanAmount\":300000000,\"annualInterestRate\":10,\"termMonths\":360}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("insert into loan_simulations")));
    }

    @Test
    @DisplayName("[TC-LOAN-105] ⚠️#74#4 DTI 999% 초과는 저장 실패, 응답에 SQL문 노출")
    void dtiOverflow_saveFails_andLeaksSql() throws Exception {
        postJson("/api/loan/calculate",
                "{\"loanAmount\":300000000,\"annualInterestRate\":4.5,\"termMonths\":360,\"monthlyIncome\":100000}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("DTI")));
    }

    @Test
    @DisplayName("[TC-LOAN-106] ⚠️#6#4 DTI API에 월 상환액이 없으면 400 + 내부 메시지 노출")
    void dtiMissingPayment_leaksInternalMessage() throws Exception {
        postJson("/api/loan/dti", "{\"monthlyIncome\":1000000}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("monthlyPayment")));
    }
}
