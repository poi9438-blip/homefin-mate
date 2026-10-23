package com.homefinmate.dto.loan;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

// 대출 계산 결과 (월 상환액, 총이자, 총상환액, DTI와 판정)
@Data
@Builder
public class LoanCalculationResponse {
    // 저장된 계산 id (loan_simulations.id)
    private Long simulationId;
    private BigDecimal loanAmount;
    private BigDecimal monthlyPayment;
    private BigDecimal totalInterest;
    // 총상환액 (월 상환액 * 기간)
    private BigDecimal totalPayment;
    // DTI (0~1, 예: 0.38 = 38%)
    private BigDecimal dti;
    // DTI 판정 (ACCEPTABLE: 0.60 이하, HIGH: 초과)
    private String dtiStatus;
    // DTI 계산 기준 (NEW_LOAN_ONLY: 전환 전 기록, 기존 대출 이자 미포함)
    private String dtiBasis;
    private String region;
    private String traceId;
}
