package com.homefinmate.dto.loan;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

// 대출 계산 요청 (원금, 연이율, 기간, 월 소득 필수 / 기존 대출 월 이자, 지역 선택)
@Data
public class LoanCalculationRequest {
    // 대출 원금 (원, 0보다 커야 함)
    @NotNull(message = "Loan amount is required")
    @Positive(message = "Loan amount must be greater than 0")
    private BigDecimal loanAmount;

    // 연이율 (%, 99.9999 이하)
    @NotNull(message = "Annual interest rate is required")
    @PositiveOrZero(message = "Annual interest rate must be zero or more")
    @DecimalMax(value = "99.9999", message = "Annual interest rate must be 99.9999 or less")
    private BigDecimal annualInterestRate;

    // 대출 기간 (개월, 1~600)
    @NotNull(message = "Loan term is required")
    @Positive(message = "Term must be at least 1 month")
    @Max(value = 600, message = "Term cannot exceed 600 months")
    private Integer termMonths;

    // 월 소득 (필수, DTI 계산용)
    @NotNull(message = "Monthly income is required")
    @Positive(message = "Monthly income must be positive")
    private BigDecimal monthlyIncome;

    // 기존 대출 월 이자 (선택, 없으면 0, DTI 계산용)
    @PositiveOrZero(message = "Existing monthly interest must be zero or more")
    private BigDecimal existingMonthlyInterest;

    // 지역 (선택)
    private String region;

    private String traceId;
    private String eventVersion = "v1";
}
