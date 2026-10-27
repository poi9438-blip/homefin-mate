package com.homefinmate.service;

import com.homefinmate.dto.analyze.AnalyzeRequest;
import com.homefinmate.dto.loan.LoanCalculationRequest;
import com.homefinmate.dto.policy.PolicyRecommendationRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// 정책대출 입력 변환·확인 (통합 분석, RiskWatch 평가, FinFlow 신청이 함께 씀)
final class PolicyLoanInputs {

    private static final Set<String> HOUSE_OWNERSHIP = Set.of("NONE", "RENT", "OWN");
    private static final BigDecimal MAX_AMOUNT = BigDecimal.valueOf(1_000_000_000L);
    private static final BigDecimal MAX_DTI = new BigDecimal("9.9999");

    private PolicyLoanInputs() {
    }

    // 확인하는 항목 (API 칸 이름: 서버 오류 메시지용, 화면 이름: 판정 안 함 이유용)
    enum Field {
        PROPERTY_TYPE("propertyType", "주택 보유 여부"),
        NEWBORN_WITHIN_2_YEARS("newbornWithin2Years", "2년 내 출산·입양 여부"),
        DUAL_INCOME("dualIncome", "맞벌이 여부"),
        LOAN_AMOUNT("loanAmount", "대출 금액(1원~10억 원, 원 단위)"),
        HOUSE_PRICE("housePrice", "주택 가격(원 단위)"),
        HOUSEHOLD_ANNUAL_INCOME("householdAnnualIncome", "부부합산 연소득(원 단위)"),
        TERM_MONTHS("termMonths", "대출 기간(12~600개월)"),
        CHILD_COUNT("childCount", "자녀 수(0~20명)"),
        DTI("dti", "DTI"),
        REGION("region", "지역(20자 이하)");

        final String apiName;
        final String label;

        Field(String apiName, String label) {
            this.apiName = apiName;
            this.label = label;
        }

        static String labels(List<Field> fields) {
            return String.join(", ", fields.stream().map(f -> f.label).toList());
        }

        static String apiNames(List<Field> fields) {
            return String.join(", ", fields.stream().map(f -> f.apiName).toList());
        }
    }

    // 원 단위로 확인한 값 (범위 밖 항목이 있으면 outOfRange에, 그 항목 값은 null)
    record Checked(List<Field> outOfRange, Long requestedAmount, Long housePrice, Long householdAnnualIncome) {
    }

    // 대출 계산 요청 (저장 없이 계산만)
    static LoanCalculationRequest loanRequest(AnalyzeRequest r) {
        LoanCalculationRequest loanReq = new LoanCalculationRequest();
        loanReq.setLoanAmount(r.getLoanAmount());
        loanReq.setAnnualInterestRate(r.getAnnualInterestRate());
        loanReq.setTermMonths(r.getTermMonths());
        loanReq.setMonthlyIncome(r.getMonthlyIncome());
        loanReq.setExistingMonthlyInterest(r.getExistingMonthlyInterest());
        loanReq.setRegion(r.getRegion());
        loanReq.setTraceId(r.getTraceId());
        return loanReq;
    }

    // 정책 판정 요청 (대출 계산 결과의 DTI 사용)
    static PolicyRecommendationRequest policyRequest(AnalyzeRequest r, BigDecimal dti) {
        PolicyRecommendationRequest policyReq = new PolicyRecommendationRequest();
        policyReq.setIncome(r.getMonthlyIncome());
        policyReq.setLoanAmount(r.getLoanAmount());
        policyReq.setDti(dti);
        policyReq.setPropertyType(r.getPropertyType());
        policyReq.setRegion(r.getRegion());
        // 정책대출 판정용 입력 전달 (주택 가격, 부부합산 연소득, 가구 조건)
        policyReq.setHousePrice(r.getHousePrice());
        policyReq.setHouseholdAnnualIncome(r.getHouseholdAnnualIncome());
        policyReq.setFirstHome(r.getFirstHome());
        policyReq.setNewlywed(r.getNewlywed());
        policyReq.setChildCount(r.getChildCount());
        // 금리 비교용 대출 기간, 신생아 특례 판정용 입력
        policyReq.setTermMonths(r.getTermMonths());
        policyReq.setNewbornWithin2Years(r.getNewbornWithin2Years());
        policyReq.setDualIncome(r.getDualIncome());
        policyReq.setTraceId(r.getTraceId());
        policyReq.setEventVersion(r.getEventVersion());
        return policyReq;
    }

    // 통합 분석에서는 선택이지만 RiskWatch·FinFlow에는 필수인 입력 중 빠진 것
    static List<Field> missing(AnalyzeRequest r) {
        List<Field> missing = new ArrayList<>();
        if (r.getPropertyType() == null || r.getPropertyType().isBlank())
            missing.add(Field.PROPERTY_TYPE);
        if (r.getNewbornWithin2Years() == null)
            missing.add(Field.NEWBORN_WITHIN_2_YEARS);
        if (r.getDualIncome() == null)
            missing.add(Field.DUAL_INCOME);
        return missing;
    }

    // RiskWatch·FinFlow 입력 범위 확인 (빠진 입력이 없을 때 부름)
    static Checked check(AnalyzeRequest r, BigDecimal dti) {
        List<Field> outOfRange = new ArrayList<>();
        Long amount = whole(r.getLoanAmount(), BigDecimal.ONE, MAX_AMOUNT);
        if (amount == null)
            outOfRange.add(Field.LOAN_AMOUNT);
        Long housePrice = whole(r.getHousePrice(), BigDecimal.ONE, null);
        if (housePrice == null)
            outOfRange.add(Field.HOUSE_PRICE);
        Long income = whole(r.getHouseholdAnnualIncome(), BigDecimal.ZERO, null);
        if (income == null)
            outOfRange.add(Field.HOUSEHOLD_ANNUAL_INCOME);
        if (r.getTermMonths() == null || r.getTermMonths() < 12 || r.getTermMonths() > 600)
            outOfRange.add(Field.TERM_MONTHS);
        if (r.getChildCount() == null || r.getChildCount() < 0 || r.getChildCount() > 20)
            outOfRange.add(Field.CHILD_COUNT);
        if (!HOUSE_OWNERSHIP.contains(r.getPropertyType()))
            outOfRange.add(Field.PROPERTY_TYPE);
        if (dti == null || dti.signum() < 0 || dti.compareTo(MAX_DTI) > 0 || dti.stripTrailingZeros().scale() > 4)
            outOfRange.add(Field.DTI);
        if (r.getRegion() != null && r.getRegion().length() > 20)
            outOfRange.add(Field.REGION);
        return new Checked(outOfRange, amount, housePrice, income);
    }

    // 지역은 비어 있으면 보내지 않음
    static String region(AnalyzeRequest r) {
        return r.getRegion() == null || r.getRegion().isBlank() ? null : r.getRegion();
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
