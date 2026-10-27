package com.homefinmate.dto.application;

import java.math.BigDecimal;

// FinFlow 신청 접수 본문 (FinFlow 업무 명세 3.1, 금액은 원 단위 정수, simulationRef는 HomeFin 계산 기록 id)
public record FinFlowApplicationRequest(String productCode, String applicantRef, long requestedAmount,
        int termMonths, long housePrice, long householdAnnualIncome, BigDecimal dti, boolean firstHome,
        boolean newlywed, boolean newbornWithin2Years, int childCount, String houseOwnership, boolean dualIncome,
        String region, Long simulationRef) {
}
