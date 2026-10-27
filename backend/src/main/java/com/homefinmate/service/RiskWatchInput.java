package com.homefinmate.service;

import com.homefinmate.dto.analyze.AnalyzeRequest;
import com.homefinmate.dto.loan.LoanCalculationResponse;
import com.homefinmate.dto.policy.PolicyRecommendationResponse;
import com.homefinmate.dto.risk.RiskEvaluationRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// RiskWatch 평가 요청 만들기 (추천 상품 하나, 입력이 모두 있고 RiskWatch 범위 안일 때만, 아니면 이유)
final class RiskWatchInput {

    private static final Set<String> HOUSE_OWNERSHIP = Set.of("NONE", "RENT", "OWN");
    private static final BigDecimal MAX_AMOUNT = BigDecimal.valueOf(1_000_000_000L);
    private static final BigDecimal MAX_DTI = new BigDecimal("9.9999");

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

        List<String> missing = new ArrayList<>();
        if (r.getPropertyType() == null || r.getPropertyType().isBlank())
            missing.add("주택 보유 여부");
        if (r.getNewbornWithin2Years() == null)
            missing.add("2년 내 출산·입양 여부");
        if (r.getDualIncome() == null)
            missing.add("맞벌이 여부");
        if (!missing.isEmpty()) {
            return skip(String.join(", ", missing) + "를 입력하면 " + policy.getRecommendedProduct()
                    + " 심사 기준으로 판정합니다.");
        }

        // RiskWatch 입력 범위 (RiskWatch 업무 명세 3장): 금액은 원 단위 정수, 기간 12~600개월, 자녀 0~20명
        List<String> outOfRange = new ArrayList<>();
        Long amount = whole(r.getLoanAmount(), BigDecimal.ONE, MAX_AMOUNT);
        if (amount == null)
            outOfRange.add("대출 금액(1원~10억 원, 원 단위)");
        Long housePrice = whole(r.getHousePrice(), BigDecimal.ONE, null);
        if (housePrice == null)
            outOfRange.add("주택 가격(원 단위)");
        Long income = whole(r.getHouseholdAnnualIncome(), BigDecimal.ZERO, null);
        if (income == null)
            outOfRange.add("부부합산 연소득(원 단위)");
        if (r.getTermMonths() == null || r.getTermMonths() < 12 || r.getTermMonths() > 600)
            outOfRange.add("대출 기간(12~600개월)");
        if (r.getChildCount() == null || r.getChildCount() < 0 || r.getChildCount() > 20)
            outOfRange.add("자녀 수(0~20명)");
        if (!HOUSE_OWNERSHIP.contains(r.getPropertyType()))
            outOfRange.add("주택 보유 여부");
        BigDecimal dti = loan == null ? null : loan.getDti();
        if (dti == null || dti.signum() < 0 || dti.compareTo(MAX_DTI) > 0 || dti.stripTrailingZeros().scale() > 4)
            outOfRange.add("DTI");
        if (r.getRegion() != null && r.getRegion().length() > 20)
            outOfRange.add("지역(20자 이하)");
        if (!outOfRange.isEmpty()) {
            return skip("입력값이 심사 기준 판정 범위를 벗어나 판정하지 않았습니다: " + String.join(", ", outOfRange) + ".");
        }

        return new Plan(RiskEvaluationRequest.builder()
                .productCode(productCode)
                .applicantRef("homefin:" + userId)
                .requestedAmount(amount)
                .termMonths(r.getTermMonths())
                .housePrice(housePrice)
                .householdAnnualIncome(income)
                .dti(dti)
                .houseOwnership(r.getPropertyType())
                .firstHome(Boolean.TRUE.equals(r.getFirstHome()))
                .newlywed(Boolean.TRUE.equals(r.getNewlywed()))
                .newbornWithin2Years(r.getNewbornWithin2Years())
                .dualIncome(r.getDualIncome())
                .childCount(r.getChildCount())
                .region(r.getRegion() == null || r.getRegion().isBlank() ? null : r.getRegion())
                .traceId(traceId)
                .build(), null);
    }

    private static Plan skip(String reason) {
        return new Plan(null, reason);
    }

    // 원 단위 정수이고 범위 안이면 그 값 (소수가 있거나 범위 밖이면 null, 소수를 버려서 보내지 않음)
    private static Long whole(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value == null || value.compareTo(min) < 0 || (max != null && value.compareTo(max) > 0)) {
            return null;
        }
        try {
            return value.longValueExact();
        } catch (ArithmeticException e) {
            return null;
        }
    }
}
