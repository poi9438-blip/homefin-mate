package com.homefinmate.dto.policy;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.math.BigDecimal;

// 정책 추천 요청 (소득, 대출 금액, DTI, 주택 보유, 지역, 주택 가격·가구 조건)
@Data
public class PolicyRecommendationRequest {
    // 월 소득 (income 또는 monthlyIncome)
    @JsonAlias("monthlyIncome")
    private BigDecimal income;

    // 대출 금액
    private BigDecimal loanAmount;

    // DTI (0~1, 대출 계산 결과의 dti)
    private BigDecimal dti;

    // 주택 보유 현황 (NONE, OWN, RENT)
    private String propertyType;

    // 지역
    private String region;

    // 주택 가격 (원, 선택)
    private BigDecimal housePrice;

    // 생애최초 주택 구입 여부 (선택)
    private Boolean firstHome;

    // 신혼가구 여부 (선택, 혼인 7년 이내)
    private Boolean newlywed;

    // 미성년 자녀 수 (선택)
    private Integer childCount;

    // 부부합산 연소득 (원, 선택). 있으면 소득 판정에 이 값을 쓰고, 없으면 월 소득 × 12(본인 기준)
    private BigDecimal householdAnnualIncome;

    private String traceId;
    private String eventVersion = "v1";
}
