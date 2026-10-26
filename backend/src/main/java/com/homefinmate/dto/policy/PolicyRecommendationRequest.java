package com.homefinmate.dto.policy;

import com.homefinmate.common.TraceIdCarrier;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.math.BigDecimal;

// 정책 추천 요청 (소득, 대출 금액, DTI, 주택 보유, 지역, 주택 가격·가구 조건)
@Data
public class PolicyRecommendationRequest implements TraceIdCarrier {
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

    // 대출 기간 (개월, 선택, 금리 비교용)
    private Integer termMonths;

    // 대출 신청일 기준 2년 안에 출산·입양한 가구인지 (선택)
    private Boolean newbornWithin2Years;

    // 맞벌이 여부 (선택, 신생아 특례는 맞벌이면 부부합산 2억 원까지)
    private Boolean dualIncome;

    // 요청 traceId (보낸 값과 관계없이 서버가 X-Trace-Id 기준 값으로 덮어씀, #90)
    private String traceId;
    private String eventVersion = "v1";
}
