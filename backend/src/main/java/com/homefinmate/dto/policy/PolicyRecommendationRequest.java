package com.homefinmate.dto.policy;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.math.BigDecimal;

// 정책 추천 요청 (소득, 기존 부채, 대출 금액, DSR, 주택 보유, 지역)
@Data
public class PolicyRecommendationRequest {
    // 월 소득 (income 또는 monthlyIncome)
    @JsonAlias("monthlyIncome")
    private BigDecimal income;

    // 기존 부채
    private BigDecimal existingDebt;

    // 대출 금액
    private BigDecimal loanAmount;

    // DSR (%)
    private BigDecimal dsr;

    // 주택 보유 현황 (NONE, OWN, RENT)
    private String propertyType;

    // 지역
    private String region;

    private String traceId;
    private String eventVersion = "v1";
}
