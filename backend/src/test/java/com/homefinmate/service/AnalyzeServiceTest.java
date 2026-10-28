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
import com.homefinmate.integration.RiskWatchClient;
import com.homefinmate.metrics.BusinessMetrics;
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
    AnalyzeResultSaver analyzeResultSaver;
    @Mock
    BusinessMetrics metrics;
    @InjectMocks
    AnalyzeService analyzeService;

    @BeforeEach
    void setUp() {
        when(loanService.compute(any())).thenReturn(LoanCalculationResponse.builder()
                .monthlyPayment(new BigDecimal("1520000"))
                .dti(new BigDecimal("0.3800")).dtiStatus("ACCEPTABLE").build());
        when(analyzeResultSaver.save(any(), any(), any())).thenReturn(100L);
        when(policyService.recommend(any()))
                .thenReturn(PolicyRecommendationResponse.builder().eligibleCount(2)
                        .recommendedProduct("보금자리론").recommendedProductCode("BOGEUMJARI").build());
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
        r.setHousePrice(new BigDecimal("600000000"));
        r.setHouseholdAnnualIncome(new BigDecimal("48000000"));
        r.setFirstHome(true);
        r.setNewlywed(false);
        r.setChildCount(0);
        r.setNewbornWithin2Years(false);
        r.setDualIncome(false);
        return r;
    }

    // ① 계산 → ② → ③ → ④ → ⑤ 저장 순서, 결과 4개와 저장된 id를 묶어 반환
    @Test
    @DisplayName("[TC-ANLZ-001] 대출 계산 → 정책 → 위험 → 지역 → 저장 순서로 호출하고 결과를 묶음")
    void analyze_callsInOrder_andCombines() {
        AnalyzeResponse res = analyzeService.analyze(request("부산"), 7L);

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

    // 추천 상품 하나로 RiskWatch 평가 요청 (금액은 원 단위 정수, DTI는 ①의 결과), 기존 대출 월 이자는 ①의 DTI 계산에
    @Test
    @DisplayName("[TC-ANLZ-201] 추천 상품으로 위험 평가 요청: 상품 코드, homefin:{id}, 원 단위 금액, 대출 결과 DTI, 기간·주택 가격·연소득·주택 보유·가구 조건·지역·traceId")
    void analyze_requestsRiskWatchWithRecommendedProduct() {
        analyzeService.analyze(request("부산"), 7L);

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
        RiskEvaluationRequest risk = riskReq.getValue();
        assertThat(risk.getProductCode()).isEqualTo("BOGEUMJARI");
        assertThat(risk.getApplicantRef()).isEqualTo("homefin:7");
        assertThat(risk.getRequestedAmount()).isEqualTo(300_000_000L);
        assertThat(risk.getTermMonths()).isEqualTo(360);
        assertThat(risk.getHousePrice()).isEqualTo(600_000_000L);
        assertThat(risk.getHouseholdAnnualIncome()).isEqualTo(48_000_000L);
        assertThat(risk.getDti()).isEqualByComparingTo("0.3800");
        assertThat(risk.getHouseOwnership()).isEqualTo("NONE");
        assertThat(risk.isFirstHome()).isTrue();
        assertThat(risk.isNewlywed()).isFalse();
        assertThat(risk.isNewbornWithin2Years()).isFalse();
        assertThat(risk.isDualIncome()).isFalse();
        assertThat(risk.getChildCount()).isZero();
        assertThat(risk.getRegion()).isEqualTo("부산");
        assertThat(risk.getTraceId()).isEqualTo("hfm-analyze-1");
    }

    // 지역이 비어 있으면 ④ 지역 분석을 부르지 않고 region은 null
    @Test
    @DisplayName("[TC-ANLZ-003] 지역이 비어 있으면 지역 분석을 건너뜀")
    void analyze_blankRegion_skipsRegionAnalysis() {
        AnalyzeResponse res = analyzeService.analyze(request("  "), 7L);

        verifyNoInteractions(analysisService);
        assertThat(res.getRegion()).isNull();
    }

    // ③에서 예외가 나면 저장하지 않고 실패
    @Test
    @DisplayName("[TC-ANLZ-004] 위험 평가에서 예외가 나면 아무것도 저장하지 않고 실패 (#5)")
    void analyze_riskThrows_nothingSaved() {
        when(riskWatchClient.evaluateRisk(any())).thenThrow(new RuntimeException("audit save failed"));

        assertThatThrownBy(() -> analyzeService.analyze(request("부산"), 7L))
                .hasMessage("audit save failed");

        verifyNoInteractions(analysisService, analyzeResultSaver);
    }

    // 대출 화면에서 받은 정책대출 판정용 입력이 정책 판정 요청에 그대로 전달되는지
    @Test
    @DisplayName("[TC-ANLZ-005] 주택 가격·부부합산 연소득·생애최초·신혼·자녀 수·2년 내 출산·맞벌이·대출 기간을 정책 판정에 전달")
    void analyze_passesPolicyInputs() {
        AnalyzeRequest req = request("부산");
        req.setHousePrice(new BigDecimal("400000000"));
        req.setHouseholdAnnualIncome(new BigDecimal("55000000"));
        req.setFirstHome(true);
        req.setNewlywed(false);
        req.setChildCount(1);
        req.setNewbornWithin2Years(true);
        req.setDualIncome(false);

        analyzeService.analyze(req, 7L);

        ArgumentCaptor<PolicyRecommendationRequest> policyReq = ArgumentCaptor
                .forClass(PolicyRecommendationRequest.class);
        verify(policyService).recommend(policyReq.capture());
        assertThat(policyReq.getValue().getHousePrice()).isEqualByComparingTo("400000000");
        assertThat(policyReq.getValue().getHouseholdAnnualIncome()).isEqualByComparingTo("55000000");
        assertThat(policyReq.getValue().getFirstHome()).isTrue();
        assertThat(policyReq.getValue().getNewlywed()).isFalse();
        assertThat(policyReq.getValue().getChildCount()).isEqualTo(1);
        // 신생아 특례 판정 입력과 금리 비교용 대출 기간도 그대로 전달
        assertThat(policyReq.getValue().getNewbornWithin2Years()).isTrue();
        assertThat(policyReq.getValue().getDualIncome()).isFalse();
        assertThat(policyReq.getValue().getTermMonths()).isEqualTo(360);
    }

    private void assertNotEvaluated(AnalyzeResponse res, String... reasonParts) {
        verify(riskWatchClient, never()).evaluateRisk(any());
        assertThat(res.getRisk().getStatus()).isEqualTo(RiskEvaluationResponse.Status.NOT_EVALUATED);
        assertThat(res.getRisk().isAvailable()).isFalse();
        assertThat(res.getRisk().getTraceId()).isEqualTo("hfm-analyze-1");
        assertThat(res.getRisk().getRecommendation()).contains(reasonParts);
        // 판정을 하지 않아도 분석은 끝까지 (지역 분석·저장)
        verify(analyzeResultSaver).save(eq(7L), any(), any());
    }

    @Test
    @DisplayName("[TC-ANLZ-202] 추천 상품이 없으면 RiskWatch를 부르지 않고 risk NOT_EVALUATED + 한국어 이유, 분석은 계속")
    void noRecommendedProduct_notEvaluated() {
        when(policyService.recommend(any())).thenReturn(PolicyRecommendationResponse.builder().eligibleCount(0).build());

        assertNotEvaluated(analyzeService.analyze(request("부산"), 7L), "추천 상품이 없어");
    }

    @Test
    @DisplayName("[TC-ANLZ-203] 주택 보유·2년 내 출산·입양·맞벌이 중 빠진 입력이 있으면 부르지 않고 NOT_EVALUATED, 이유에 빠진 항목")
    void missingRiskInputs_notEvaluated() {
        AnalyzeRequest req = request("부산");
        req.setPropertyType(null);
        req.setDualIncome(null);

        AnalyzeResponse res = analyzeService.analyze(req, 7L);
        assertNotEvaluated(res, "주택 보유 여부", "맞벌이 여부", "보금자리론");
        // 입력한 항목(2년 내 출산·입양)은 빠졌다고 하지 않음
        assertThat(res.getRisk().getRecommendation()).doesNotContain("2년 내 출산");
    }

    @Test
    @DisplayName("[TC-ANLZ-204] RiskWatch 입력 범위: 기간 12개월·자녀 20명은 부르고, 11개월·소수 금액·자녀 21명·없는 주택 보유 값은 부르지 않음")
    void riskWatchInputRange() {
        // 범위 안 (경계)
        AnalyzeRequest inside = request("부산");
        inside.setTermMonths(12);
        inside.setChildCount(20);
        analyzeService.analyze(inside, 7L);
        verify(riskWatchClient).evaluateRisk(any());

        // 범위 밖: 하나씩
        java.util.Map<String, java.util.function.Consumer<AnalyzeRequest>> outside = new java.util.LinkedHashMap<>();
        outside.put("대출 기간", r -> r.setTermMonths(11));
        outside.put("대출 금액", r -> r.setLoanAmount(new BigDecimal("300000000.5")));
        outside.put("자녀 수", r -> r.setChildCount(21));
        outside.put("주택 보유 여부", r -> r.setPropertyType("TWO"));
        for (var c : outside.entrySet()) {
            clearInvocations(riskWatchClient, analyzeResultSaver);
            AnalyzeRequest req = request("부산");
            c.getValue().accept(req);
            AnalyzeResponse res = analyzeService.analyze(req, 7L);
            verify(riskWatchClient, never()).evaluateRisk(any());
            assertThat(res.getRisk().getStatus()).as(c.getKey()).isEqualTo(RiskEvaluationResponse.Status.NOT_EVALUATED);
            assertThat(res.getRisk().getRecommendation()).as(c.getKey()).contains("범위를 벗어나", c.getKey());
        }
    }

    // 업무 지표: RiskWatch 결과마다 한 번 기록 (판정 / 응답 없음 / 부르지 않음)
    @Test
    @DisplayName("[TC-OBS-201] 통합 분석의 RiskWatch 결과: 판정 / 응답 없음 / 입력 모자람(부르지 않음) → 지표 EVALUATED / UNAVAILABLE / NOT_EVALUATED")
    void recordsRiskWatchResultMetric() {
        when(riskWatchClient.evaluateRisk(any())).thenReturn(RiskEvaluationResponse.builder()
                .status(RiskEvaluationResponse.Status.EVALUATED).available(true).build());
        analyzeService.analyze(request("부산"), 7L);
        verify(metrics).riskEvaluation(RiskEvaluationResponse.Status.EVALUATED);

        when(riskWatchClient.evaluateRisk(any())).thenReturn(RiskEvaluationResponse.builder()
                .status(RiskEvaluationResponse.Status.UNAVAILABLE).available(false).build());
        analyzeService.analyze(request("부산"), 7L);
        verify(metrics).riskEvaluation(RiskEvaluationResponse.Status.UNAVAILABLE);

        AnalyzeRequest missing = request("부산");
        missing.setDualIncome(null);
        analyzeService.analyze(missing, 7L);
        verify(metrics).riskEvaluation(RiskEvaluationResponse.Status.NOT_EVALUATED);
        verify(riskWatchClient, times(2)).evaluateRisk(any());
        verifyNoMoreInteractions(metrics);
    }
}
