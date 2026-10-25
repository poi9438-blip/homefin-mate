package com.homefinmate.service;

import com.homefinmate.common.BusinessException;
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
import org.springframework.http.HttpStatus;

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
    AnalyzeResultSaver analyzeResultSaver;
    @InjectMocks
    AnalyzeService analyzeService;

    @BeforeEach
    void setUp() {
        when(userRepository.findByEmail("user@test.com"))
                .thenReturn(Optional.of(User.builder().id(7L).email("user@test.com").build()));
        when(loanService.compute(any())).thenReturn(LoanCalculationResponse.builder()
                .monthlyPayment(new BigDecimal("1520000"))
                .dti(new BigDecimal("0.3800")).dtiStatus("ACCEPTABLE").build());
        when(analyzeResultSaver.save(any(), any(), any())).thenReturn(100L);
        when(policyService.recommend(any()))
                .thenReturn(PolicyRecommendationResponse.builder().eligibleCount(2).build());
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
        r.setExistingMonthlyInterest(new BigDecimal("500000"));
        r.setPropertyType("NONE");
        r.setRegion(region);
        r.setTraceId("hfm-analyze-1");
        return r;
    }

    // ① 계산 → ② → ③ → ④ → ⑤ 저장 순서, 결과 4개와 저장된 id를 묶어 반환
    @Test
    @DisplayName("[TC-ANLZ-001] 대출 계산 → 정책 → 위험 → 지역 → 저장 순서로 호출하고 결과를 묶음")
    void analyze_callsInOrder_andCombines() {
        AnalyzeResponse res = analyzeService.analyze(request("부산"), "user@test.com");

        InOrder order = inOrder(loanService, policyService, riskWatchClient, analysisService, analyzeResultSaver);
        order.verify(loanService).compute(any());
        order.verify(policyService).recommend(any());
        order.verify(riskWatchClient).evaluateRisk(any());
        order.verify(analysisService).getRegionAnalysis("부산", "hfm-analyze-1");
        order.verify(analyzeResultSaver).save(eq(7L), any(), any());

        assertThat(res.getLoan().getSimulationId()).isEqualTo(100L);
        assertThat(res.getPolicy().getEligibleCount()).isEqualTo(2);
        assertThat(res.getRisk().isAvailable()).isFalse();
        assertThat(res.getRegion().getRegion()).isEqualTo("부산");
        assertThat(res.getTraceId()).isEqualTo("hfm-analyze-1");
    }

    // 기존 대출 월 이자는 대출 계산(DTI)에, ①의 DTI는 정책, 위험 평가에 전달
    // 위험 평가의 dsr은 호환용 예전 계산(월 상환액 ÷ 월 소득)
    @Test
    @DisplayName("[TC-ANLZ-002] 기존 대출 월 이자는 DTI 계산에, 대출 결과 DTI는 정책·위험 평가에 전달 (위험 평가 dsr은 예전 계산)")
    void analyze_passesDtiAndTraceId() {
        analyzeService.analyze(request("부산"), "user@test.com");

        ArgumentCaptor<LoanCalculationRequest> loanReq = ArgumentCaptor.forClass(LoanCalculationRequest.class);
        ArgumentCaptor<PolicyRecommendationRequest> policyReq = ArgumentCaptor
                .forClass(PolicyRecommendationRequest.class);
        ArgumentCaptor<RiskEvaluationRequest> riskReq = ArgumentCaptor.forClass(RiskEvaluationRequest.class);
        verify(loanService).compute(loanReq.capture());
        verify(policyService).recommend(policyReq.capture());
        verify(riskWatchClient).evaluateRisk(riskReq.capture());

        assertThat(loanReq.getValue().getTraceId()).isEqualTo("hfm-analyze-1");
        assertThat(loanReq.getValue().getExistingMonthlyInterest()).isEqualByComparingTo("500000");
        assertThat(policyReq.getValue().getDti()).isEqualByComparingTo("0.3800");
        assertThat(riskReq.getValue().getDti()).isEqualByComparingTo("0.3800");
        assertThat(riskReq.getValue().getDsr()).isEqualByComparingTo("0.3800");
        assertThat(riskReq.getValue().getUserId()).isEqualTo("7");
        assertThat(riskReq.getValue().getHouseOwnership()).isEqualTo("NONE");
        assertThat(riskReq.getValue().getTraceId()).isEqualTo("hfm-analyze-1");
    }

    // 지역이 비어 있으면 ④ 지역 분석을 부르지 않고 region은 null
    @Test
    @DisplayName("[TC-ANLZ-003] 지역이 비어 있으면 지역 분석을 건너뜀")
    void analyze_blankRegion_skipsRegionAnalysis() {
        AnalyzeResponse res = analyzeService.analyze(request("  "), "user@test.com");

        verifyNoInteractions(analysisService);
        assertThat(res.getRegion()).isNull();
    }

    // ③에서 예외가 나면 저장하지 않고 실패
    @Test
    @DisplayName("[TC-ANLZ-004] 위험 평가에서 예외가 나면 아무것도 저장하지 않고 실패 (#5)")
    void analyze_riskThrows_nothingSaved() {
        when(riskWatchClient.evaluateRisk(any())).thenThrow(new RuntimeException("audit save failed"));

        assertThatThrownBy(() -> analyzeService.analyze(request("부산"), "user@test.com"))
                .hasMessage("audit save failed");

        verifyNoInteractions(analysisService, analyzeResultSaver);
    }

    // 대출 화면에서 받은 정책대출 판정용 입력이 정책 판정 요청에 그대로 전달되는지
    @Test
    @DisplayName("[TC-ANLZ-005] 주택 가격·부부합산 연소득·생애최초·신혼·자녀 수를 정책 판정에 전달")
    void analyze_passesPolicyInputs() {
        AnalyzeRequest req = request("부산");
        req.setHousePrice(new BigDecimal("400000000"));
        req.setHouseholdAnnualIncome(new BigDecimal("55000000"));
        req.setFirstHome(true);
        req.setNewlywed(false);
        req.setChildCount(1);

        analyzeService.analyze(req, "user@test.com");

        ArgumentCaptor<PolicyRecommendationRequest> policyReq = ArgumentCaptor
                .forClass(PolicyRecommendationRequest.class);
        verify(policyService).recommend(policyReq.capture());
        assertThat(policyReq.getValue().getHousePrice()).isEqualByComparingTo("400000000");
        assertThat(policyReq.getValue().getHouseholdAnnualIncome()).isEqualByComparingTo("55000000");
        assertThat(policyReq.getValue().getFirstHome()).isTrue();
        assertThat(policyReq.getValue().getNewlywed()).isFalse();
        assertThat(policyReq.getValue().getChildCount()).isEqualTo(1);
    }

    // 토큰의 사용자가 DB에 없으면 외부 호출 없이 401
    @Test
    @DisplayName("[TC-ANLZ-006] 토큰의 사용자가 DB에 없으면 401 업무 오류, 계산·정책·RiskWatch·지역 분석·저장 모두 호출 안 함 (#58)")
    void analyze_unknownUser_unauthorizedBeforeExternalCalls() {
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> analyzeService.analyze(request("부산"), "ghost@test.com"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));

        verifyNoInteractions(loanService, policyService, riskWatchClient, analysisService, analyzeResultSaver);
    }
}
