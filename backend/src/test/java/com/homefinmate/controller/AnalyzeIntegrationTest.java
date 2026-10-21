package com.homefinmate.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homefinmate.entity.UserActivityLog;
import com.homefinmate.repository.AuditLogRepository;
import com.homefinmate.repository.LoanSimulationRepository;
import com.homefinmate.repository.UserActivityLogRepository;
import com.homefinmate.repository.UserRepository;
import com.homefinmate.service.AuditService;
import com.homefinmate.service.PolicyService;
import com.homefinmate.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.homefinmate.support.ApiTestSupport.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 통합 분석 HTTP + DB 통합 테스트 (외부 서비스 장애 시 fallback, 부분 저장)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class AnalyzeIntegrationTest {

        @Autowired
        MockMvc mockMvc;
        @Autowired
        ObjectMapper objectMapper;
        @Autowired
        UserRepository userRepository;
        @Autowired
        LoanSimulationRepository loanSimulationRepository;
        @Autowired
        UserActivityLogRepository userActivityLogRepository;
        @Autowired
        AuditLogRepository auditLogRepository;

        @SpyBean
        PolicyService policyService;
        @SpyBean
        AuditService auditService;

        private String email;
        private String token;
        private String traceId;

        @BeforeEach
        void setUp() throws Exception {
                ApiTestSupport api = new ApiTestSupport(mockMvc, objectMapper);
                email = ApiTestSupport.uniqueEmail("analyze");
                token = api.signupAndLogin(email);
                traceId = "hfm-it-" + UUID.randomUUID();
        }

        private String body() {
                return "{\"loanAmount\":300000000,\"annualInterestRate\":4.5,\"termMonths\":360,"
                                + "\"monthlyIncome\":4000000,\"propertyType\":\"NONE\",\"region\":\"부산\","
                                + "\"housePrice\":400000000,\"householdAnnualIncome\":48000000,"
                                + "\"firstHome\":true,\"newlywed\":false,\"childCount\":0,"
                                + "\"traceId\":\"" + traceId + "\"}";
        }

        private Long userId() {
                return userRepository.findByEmail(email).orElseThrow().getId();
        }

        @Test
        @DisplayName("[TC-ANLZ-101] 외부 서비스가 모두 죽어도 200, 위험 평가는 available=false, 계산 1건 저장, 연동 실패 감사 기록")
        void externalServicesDown_returns200WithFallback_andPersists() throws Exception {
                mockMvc.perform(post("/api/analyze").header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON).content(body()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.loan.monthlyPayment").value(1520055.93))
                                .andExpect(jsonPath("$.data.risk.available").value(false))
                                .andExpect(jsonPath("$.data.region.region").value("부산"))
                                .andExpect(jsonPath("$.traceId").value(traceId));

                assertThat(loanSimulationRepository.findByUserIdOrderByCreatedAtDesc(userId()))
                                .singleElement()
                                .satisfies(sim -> assertThat(sim.getTraceId()).isEqualTo(traceId));
                assertThat(userActivityLogRepository.findByUserIdOrderByCreatedAtDesc(userId()))
                                .extracting(UserActivityLog::getAction)
                                .contains("UNIFIED_ANALYSIS");
                assertThat(auditLogRepository.findAll())
                                .filteredOn(log -> traceId.equals(log.getTraceId()))
                                .singleElement()
                                .satisfies(log -> assertThat(log.getAction())
                                                .startsWith("RISKWATCH_INTEGRATION_FAILURE: "));
        }

        @Test
        @DisplayName("[TC-ANLZ-102] 분석 중간(정책 추천)에 실패하면 실패 응답, 계산 기록 0건 (#5)")
        void failureBeforeSave_responseFails_andNothingSaved() throws Exception {
                doThrow(new IllegalStateException("policy failure for test")).when(policyService).recommend(any());

                mockMvc.perform(post("/api/analyze").header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON).content(body()))
                                .andExpect(status().isBadRequest());

                assertThat(loanSimulationRepository.findByUserIdOrderByCreatedAtDesc(userId())).isEmpty();
        }

        @Test
        @DisplayName("[TC-ANLZ-104] 저장 중 이용 기록 저장이 실패하면 계산 기록도 함께 취소 (#5)")
        void failureDuringSave_rollsBackSimulationAndActivity() throws Exception {
                doThrow(new IllegalStateException("activity save failure for test"))
                                .when(auditService).logActivity(any(), eq("UNIFIED_ANALYSIS"), any());

                mockMvc.perform(post("/api/analyze").header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON).content(body()))
                                .andExpect(status().isBadRequest());

                assertThat(loanSimulationRepository.findByUserIdOrderByCreatedAtDesc(userId())).isEmpty();
                assertThat(userActivityLogRepository.findByUserIdOrderByCreatedAtDesc(userId())).isEmpty();
        }

        @Test
        @DisplayName("[TC-ANLZ-103] ⚠️#83 분석 로그에 이메일·대출 금액·월 상환액·DSR이 평문으로 남음")
        void analyzeLogs_containPersonalAndFinancialData(CapturedOutput output) throws Exception {
                mockMvc.perform(post("/api/analyze").header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON).content(body()))
                                .andExpect(status().isOk());

                assertThat(output.getOut())
                                .contains("email=" + email)
                                .contains("loanAmount=300000000")
                                .contains("monthlyPayment=1520055.93 dsr=0.3800");
        }

        // 대출 화면 전체 입력으로 통합 분석 → 정책 추천 결과가 응답에 포함되는지 (실제 HTTP + DB)
        @Test
        @DisplayName("[TC-ANLZ-105] 정책 판정용 입력을 모두 넣으면 통합 분석 응답에 추천 상품(디딤돌대출)과 이유 포함")
        void analyzeWithPolicyInputs_returnsRecommendation() throws Exception {
                String full = "{\"loanAmount\":200000000,\"annualInterestRate\":4.5,\"termMonths\":360,"
                                + "\"monthlyIncome\":4000000,\"propertyType\":\"NONE\",\"region\":\"부산\","
                                + "\"housePrice\":400000000,\"householdAnnualIncome\":48000000,"
                                + "\"firstHome\":true,\"newlywed\":false,\"childCount\":0}";

                mockMvc.perform(post("/api/analyze").header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON).content(full))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.policy.recommendedProduct").value("내집마련 디딤돌대출"))
                                .andExpect(jsonPath("$.data.policy.recommendationReason").isNotEmpty());
        }

        // 정책대출 판정용 입력 검증: 자녀 수 음수, 주택 가격 0 이하는 400
        @Test
        @DisplayName("[TC-ANLZ-106] 자녀 수 음수·주택 가격 0이면 400 (입력 검증)")
        void invalidPolicyInputs_return400() throws Exception {
                // 다른 필수 입력은 모두 넣어서, 400이 검사하려는 값 때문에 나도록 함
                String base = "{\"loanAmount\":200000000,\"annualInterestRate\":4.5,\"termMonths\":360,"
                                + "\"monthlyIncome\":4000000,\"householdAnnualIncome\":48000000,"
                                + "\"firstHome\":true,\"newlywed\":false,";

                mockMvc.perform(post("/api/analyze").header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(base + "\"housePrice\":400000000,\"childCount\":-1}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message")
                                                .value(org.hamcrest.Matchers.containsString("childCount")));
                mockMvc.perform(post("/api/analyze").header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(base + "\"housePrice\":0,\"childCount\":0}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message")
                                                .value(org.hamcrest.Matchers.containsString("housePrice")));
        }

        // 정책 판정 입력은 필수: 비우면 판정을 못 해 추천이 나오지 않으므로 요청 단계에서 400
        @Test
        @DisplayName("[TC-ANLZ-107] 월 소득·주택 가격·부부합산 연소득·생애최초·신혼·자녀 수를 비우면 400, 계산 기록 0건")
        void missingPolicyInputs_return400() throws Exception {
                String loanOnly = "{\"loanAmount\":200000000,\"annualInterestRate\":4.5,\"termMonths\":360}";

                mockMvc.perform(post("/api/analyze").header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON).content(loanOnly))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.allOf(
                                                org.hamcrest.Matchers.containsString("monthlyIncome"),
                                                org.hamcrest.Matchers.containsString("housePrice"),
                                                org.hamcrest.Matchers.containsString("householdAnnualIncome"),
                                                org.hamcrest.Matchers.containsString("firstHome"),
                                                org.hamcrest.Matchers.containsString("newlywed"),
                                                org.hamcrest.Matchers.containsString("childCount"))));

                assertThat(loanSimulationRepository.findByUserIdOrderByCreatedAtDesc(userId())).isEmpty();
        }
}
