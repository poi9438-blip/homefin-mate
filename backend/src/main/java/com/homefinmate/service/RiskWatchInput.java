package com.homefinmate.service;

import com.homefinmate.dto.analyze.AnalyzeRequest;
import com.homefinmate.dto.loan.LoanCalculationResponse;
import com.homefinmate.dto.policy.PolicyRecommendationResponse;
import com.homefinmate.dto.risk.RiskEvaluationRequest;

import java.math.BigDecimal;
import java.util.List;

// RiskWatch 평가 요청 만들기 (추천 상품 하나, 입력이 모두 있고 RiskWatch 범위 안일 때만, 아니면 이유)
final class RiskWatchInput {

    private RiskWatchInput() {
    }

    // 요청(부를 때) 또는 부르지 않는 이유 중 하나
    record Plan(RiskEvaluationRequest request, String skipReason) {
    }

    static Plan plan(AnalyzeRequest r, LoanCalculationResponse loan, PolicyRecommendationResponse policy,
            Long userId, String traceId) {
        String productCode = policy == null ? null : policy.getRecommendedProductCode();
        if (productCode == null) {
            return skip("조건을 모두 충족한 추천 상품이 없어 심사 기준 판정을 하지 않았습니다.");
        }

        List<PolicyLoanInputs.Field> missing = PolicyLoanInputs.missing(r);
        if (!missing.isEmpty()) {
            return skip(PolicyLoanInputs.Field.labels(missing) + "를 입력하면 " + policy.getRecommendedProduct()
                    + " 심사 기준으로 판정합니다.");
        }

        // RiskWatch 입력 범위 (RiskWatch 업무 명세 3장): 금액은 원 단위 정수, 기간 12~600개월, 자녀 0~20명
        BigDecimal dti = loan == null ? null : loan.getDti();
        PolicyLoanInputs.Checked checked = PolicyLoanInputs.check(r, dti);
        if (!checked.outOfRange().isEmpty()) {
            return skip("입력값이 심사 기준 판정 범위를 벗어나 판정하지 않았습니다: "
                    + PolicyLoanInputs.Field.labels(checked.outOfRange()) + ".");
        }

        return new Plan(RiskEvaluationRequest.builder()
                .productCode(productCode)
                .applicantRef("homefin:" + userId)
                .requestedAmount(checked.requestedAmount())
                .termMonths(r.getTermMonths())
                .housePrice(checked.housePrice())
                .householdAnnualIncome(checked.householdAnnualIncome())
                .dti(dti)
                .houseOwnership(r.getPropertyType())
                .firstHome(Boolean.TRUE.equals(r.getFirstHome()))
                .newlywed(Boolean.TRUE.equals(r.getNewlywed()))
                .newbornWithin2Years(r.getNewbornWithin2Years())
                .dualIncome(r.getDualIncome())
                .childCount(r.getChildCount())
                .region(PolicyLoanInputs.region(r))
                .traceId(traceId)
                .build(), null);
    }

    private static Plan skip(String reason) {
        return new Plan(null, reason);
    }
}
