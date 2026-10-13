package com.homefinmate.dto.loan;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

// 대출 계산 결과 (월 상환액, 총이자, 총상환액, DSR과 판정)
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
    // DSR (%)
    private BigDecimal dsr;
    // DSR 판정 (ACCEPTABLE: 0.43 이하, HIGH: 초과)
    private String dsrStatus;
    private String region;
    private String traceId;
}
