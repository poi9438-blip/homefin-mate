package com.homefinmate.service;

import com.homefinmate.dto.analysis.RegionAnalysisResponse;
import com.homefinmate.dto.analyze.AnalyzeRequest;
import com.homefinmate.dto.analyze.AnalyzeResponse;
import com.homefinmate.dto.loan.LoanCalculationRequest;
import com.homefinmate.dto.loan.LoanCalculationResponse;
import com.homefinmate.dto.policy.PolicyRecommendationRequest;
import com.homefinmate.dto.policy.PolicyRecommendationResponse;
import com.homefinmate.dto.risk.RiskEvaluationRequest;
import com.homefinmate.dto.risk.RiskEvaluationResponse;
import com.homefinmate.entity.User;
import com.homefinmate.integration.RiskWatchClient;
import com.homefinmate.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// AnalyzeService 호출 순서와 값 전달 검증
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalyzeServiceTest {

    @Mock
    LoanService loanService;
    @Mock
    PolicyService policyService;
    @Mock
    RiskWatchClient riskWatchClient;
    @Mock
    AnalysisService analysisService;
    @Mock
    UserRepository userRepository;
    @Mock
    AuditService auditService;
    @InjectMocks
    AnalyzeService analyzeService;

    @BeforeEach
    void setUp() {
        when(userRepository.findByEmail("user@test.com"))
                .thenReturn(Optional.of(User.builder().id(7L).email("user@test.com").build()));
        when(loanService.calculate(any(), anyString())).thenReturn(LoanCalculationResponse.builder()
                .simulationId(100L).dsr(new BigDecimal("0.3800")).dsrStatus("ACCEPTABLE").build());
        when(policyService.recommend(any())).thenReturn(PolicyRecommendationResponse.builder().score(4).build());
        when(riskWatchClient.evaluateRisk(any())).thenReturn(RiskEvaluationResponse.builder()
                .riskLevel("UNKNOWN").riskScore(0).available(false).build());
        when(analysisService.getRegionAnalysis(anyString(), anyString()))
                .thenReturn(RegionAnalysisResponse.builder().region("부산").build());
    }

    private AnalyzeRequest request(String region) {
        AnalyzeRequest r = new AnalyzeRequest();
        r.setLoanAmount(new BigDecimal("300000000"));
        r.setAnnualInterestRate(new BigDecimal("4.5"));
        r.setTermMonths(360);
        r.setMonthlyIncome(new BigDecimal("4000000"));
        r.setExistingDebt(new BigDecimal("500000"));
        r.setPropertyType("NONE");
        r.setRegion(region);
        r.setTraceId("hfm-analyze-1");
        return r;
    }

    // ① → ② → ③ → ④ → 이용 기록 순서, 결과 4개를 묶어 반환
    @Test
    @DisplayName("대출 → 정책 → 위험 → 지역 순서로 호출하고 결과를 묶음")
    void analyze_callsInOrder_andCombines() {
        AnalyzeResponse res = analyzeService.analyze(request("부산"), "user@test.com");

        InOrder order = inOrder(loanService, policyService, riskWatchClient, analysisService, auditService);
        order.verify(loanService).calculate(any(), eq("user@test.com"));
        order.verify(policyService).recommend(any());
        order.verify(riskWatchClient).evaluateRisk(any());
        order.verify(analysisService).getRegionAnalysis("부산", "hfm-analyze-1");
        order.verify(auditService).logActivity(7L, "UNIFIED_ANALYSIS", "hfm-analyze-1");

        assertThat(res.getLoan().getSimulationId()).isEqualTo(100L);
        assertThat(res.getPolicy().getScore()).isEqualTo(4);
        assertThat(res.getRisk().isAvailable()).isFalse();
        assertThat(res.getRegion().getRegion()).isEqualTo("부산");
        assertThat(res.getTraceId()).isEqualTo("hfm-analyze-1");
    }

    // ①의 DSR을 ②③에 전달, 기존 부채는 정책에만
    @DisplayName("대출 결과 DSR을 정책·위험 평가에 전달, 기존 부채는 정책에만")
    void analyze_passesDsrAndTraceId() {
        analyzeService.analyze(request("부산"), "user@test.com");

        ArgumentCaptor<LoanCalculationRequest> loanReq = ArgumentCaptor.forClass(LoanCalculationRequest.class);
        ArgumentCaptor<PolicyRecommendationRequest> policyReq = ArgumentCaptor
                .forClass(PolicyRecommendationRequest.class);
        ArgumentCaptor<RiskEvaluationRequest> riskReq = ArgumentCaptor.forClass(RiskEvaluationRequest.class);
        verify(loanService).calculate(loanReq.capture(), anyString());
        verify(policyService).recommend(policyReq.capture());
        verify(riskWatchClient).evaluateRisk(riskReq.capture());

        assertThat(loanReq.getValue().getTraceId()).isEqualTo("hfm-analyze-1");
        assertThat(policyReq.getValue().getDsr()).isEqualByComparingTo("0.3800");
        assertThat(policyReq.getValue().getExistingDebt()).isEqualByComparingTo("500000");
        assertThat(riskReq.getValue().getDsr()).isEqualByComparingTo("0.3800");
        assertThat(riskReq.getValue().getUserId()).isEqualTo("7");
        assertThat(riskReq.getValue().getHouseOwnership()).isEqualTo("NONE");
        assertThat(riskReq.getValue().getTraceId()).isEqualTo("hfm-analyze-1");
    }

    // 지역이 비어 있으면 ④ 지역 분석을 부르지 않고 region은 null
    @Test
    @DisplayName("지역이 비어 있으면 지역 분석을 건너뜀")
    void analyze_blankRegion_skipsRegionAnalysis() {
        AnalyzeResponse res = analyzeService.analyze(request("  "), "user@test.com");

        verifyNoInteractions(analysisService);
        assertThat(res.getRegion()).isNull();
    }

    // TODO: 수정 목록 - ③에서 예외가 나면 ①은 이미 실행됐는데 분석 전체가 실패
    @Test
    @DisplayName("위험 평가에서 예외가 나면 대출 계산은 이미 실행된 채 전체 실패")
    void analyze_riskThrows_loanAlreadyCalculated() {
        when(riskWatchClient.evaluateRisk(any())).thenThrow(new RuntimeException("audit save failed"));

        assertThatThrownBy(() -> analyzeService.analyze(request("부산"), "user@test.com"))
                .hasMessage("audit save failed");

        verify(loanService).calculate(any(), anyString());
        verifyNoInteractions(analysisService);
        verify(auditService, never()).logActivity(any(), eq("UNIFIED_ANALYSIS"), any());
    }
}
