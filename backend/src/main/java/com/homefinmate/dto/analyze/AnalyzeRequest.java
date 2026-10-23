package com.homefinmate.dto.analyze;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
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

    // 월 소득 (필수, DTI 판정에 사용)
    @NotNull(message = "Monthly income is required")
    @Positive(message = "Monthly income must be positive")
    private BigDecimal monthlyIncome;

    // 기존 대출 월 이자 (선택, 없으면 0, DTI 계산에 사용)
    @PositiveOrZero(message = "Existing monthly interest must be zero or more")
    private BigDecimal existingMonthlyInterest;

    // 주택 보유 현황 (NONE: 무주택, OWN: 보유, RENT: 임차)
    private String propertyType;

    // 지역 (선택, 대출 저장,정책,위험 평가,지역 분석에 공통 사용)
    private String region;

    // 정책대출 판정용 입력 (필수: 비우면 조건을 판정할 수 없어 추천이 나오지 않음)
    // 주택 가격 (원)
    @NotNull(message = "House price is required")
    @Positive(message = "House price must be positive")
    private BigDecimal housePrice;

    // 부부합산 연소득 (원)
    @NotNull(message = "Household annual income is required")
    @Positive(message = "Household annual income must be positive")
    private BigDecimal householdAnnualIncome;

    // 생애최초 주택 구입 여부
    @NotNull(message = "First home is required")
    private Boolean firstHome;

    // 신혼가구 여부 (혼인 7년 이내)
    @NotNull(message = "Newlywed is required")
    private Boolean newlywed;

    // 미성년 자녀 수
    @NotNull(message = "Child count is required")
    @PositiveOrZero(message = "Child count must be zero or more")
    private Integer childCount;

    private String traceId;
    private String eventVersion = "v1";
}
