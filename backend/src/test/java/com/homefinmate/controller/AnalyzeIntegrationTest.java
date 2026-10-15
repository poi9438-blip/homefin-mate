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
        @D me("[TC-ANLZ-104] 저장 중 이용 기록 저장이 실패하면 계산 기록도 함께 취소 (#5)")
        vo  failureDuringSave_rollsBackSimulationAndActivity() throws Exception {
                doThrow(new IllegalStateException("activity save failure for test"))
                                .when(auditService).logActivity(any(

         
         
         
         
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
}
