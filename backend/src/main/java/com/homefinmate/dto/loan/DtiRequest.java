package com.homefinmate.dto.loan;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

// DTI 단독 계산 요청 (신규 대출 월 상환액, 기존 대출 월 이자, 월 소득)
@Data
public class DtiRequest {
    // 신규 대출 월 상환액 (원, 필수, 0 이상)
    @NotNull(message = "Monthly payment is required")
    @PositiveOrZero(message = "Monthly payment must be zero or more")
    private BigDecimal monthlyPayment;

    // 기존 대출 월 이자 (원, 없으면 0)
    @PositiveOrZero(message = "Existing monthly interest must be zero or more")
    private BigDecimal existingMonthlyInterest;

    // 월 소득 (원, 필수, 0보다 커야 함)
    @NotNull(message = "Monthly income is required")
    @Positive(message = "Monthly income must be positive")
    private BigDecimal monthlyIncome;

    private String traceId;
    private String eventVersion = "v1";
}
