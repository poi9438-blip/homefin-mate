package com.homefinmate.dto.analyze;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Max;
import lombok.Data;

import java.math.BigDecimal;

// 통합 분석 요청 (대출 + 정책 + 위험 평가 + 지역 분석 입력값)
@Data
public class AnalyzeRequest {

    // 대출 원금 (필수, 0보다 커야 함)
    @NotNull(message = "Loan amount is required")
    @Positive(message = "Loan amount must be greater than 0")
    private BigDecimal loanAmount;

    // 연이율 (%, 필수, 0보다 커야 함)
    @NotNull(message = "Annual interest rate is required")
    @Positive(message = "Annual interest rate must be greater than 0")
    private BigDecimal annualInterestRate;

    // 대출 기간 (개월, 필수, 1~600)
    @NotNull(message = "Loan term is required")
    @Positive(message = "Term must be at least 1 month")
    @Max(value = 600, message = "Term cannot exceed 600 months")
    private Integer termMonths;

    // 월 소득 (선택, 넣으면 0보다 커야 함)
    @Positive(message = "Monthly income must be positive")
    private BigDecimal monthlyIncome;

    // 기존 월 부채 상환액 (선택, 정책 추천에만 사용)
    private BigDecimal existingDebt;

    // 주택 보유 현황 (NONE: 무주택, OWN: 보유, RENT: 임차)
    private String propertyType;

    // 지역 (선택, 대출 저장,정책,위험 평가,지역 분석에 공통 사용)
    private String region;

    private String traceId;
    private String eventVersion = "v1";
}
