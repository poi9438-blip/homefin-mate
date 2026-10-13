package com.homefinmate.dto.loan;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

// 대출 계산 요청 (원금, 연이율, 기간 필수 / 월 소득, 지역 선택)
@Data
public class LoanCalculationRequest {
    // 대출 원금 (원)
    @NotNull
    private BigDecimal loanAmount;

    // 연이율 (%)
    @NotNull
    private BigDecimal annualInterestRate;

    // 대출 기간 (개월)
    @NotNull
    private Integer termMonths;

    // 월 소득 (선택, DSR 계산용)
    private BigDecimal monthlyIncome;

    // 지역 (선택)
    private String region;

    private String traceId;
    private String eventVersion = "v1";
}
