package com.homefinmate.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

// DTI 계산·판정 기준 (상품별 판정 기준은 PolicyService에 따로 둠)
public final class DtiRule {

    // 앱 판정 기준: DTI 60% 이하면 ACCEPTABLE
    public static final BigDecimal LIMIT = new BigDecimal("0.60");

    // 계산 기준: 신규 대출 월 상환액만 (DTI 전환 전 DSR 값을 옮긴 기록)
    public static final String NEW_LOAN_ONLY = "NEW_LOAN_ONLY";
    // 계산 기준: 신규 대출 월 상환액 + 기존 대출 월 이자
    public static final String WITH_EXISTING_INTEREST = "WITH_EXISTING_INTEREST";

    private DtiRule() {
    }

    // DTI 계산 (소수 4자리, 기존 대출 월 이자가 없으면 0, 소득이 없으면 0 ⚠️#64)
    public static BigDecimal calculate(BigDecimal monthlyPayment, BigDecimal existingMonthlyInterest,
            BigDecimal monthlyIncome) {
        if (monthlyIncome == null || monthlyIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal interest = existingMonthlyInterest != null ? existingMonthlyInterest : BigDecimal.ZERO;
        return monthlyPayment.add(interest).divide(monthlyIncome, 4, RoundingMode.HALF_UP);
    }

    // 60% 이하면 ACCEPTABLE, 넘거나 값이 없으면 HIGH
    public static String status(BigDecimal dti) {
        return dti != null && dti.compareTo(LIMIT) <= 0 ? "ACCEPTABLE" : "HIGH";
    }
}
