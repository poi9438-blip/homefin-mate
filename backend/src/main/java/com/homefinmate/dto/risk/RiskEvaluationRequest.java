package com.homefinmate.dto.risk;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

// RiskWatch 평가 요청 (RiskWatch 업무 명세 3장, 금액은 원 단위 정수)
@Data
@Builder
public class RiskEvaluationRequest {
    // DIDIMDOL / NEWBORN_DIDIMDOL / BOGEUMJARI (HomeFin 추천 상품)
    private String productCode;
    // homefin:{사용자 id}
    private String applicantRef;
    // HomeFin 쪽 참조 (지금은 없음: 계산 기록은 외부 호출이 끝난 뒤 저장)
    private String sourceRef;
    private long requestedAmount;
    private int termMonths;
    private long housePrice;
    private long householdAnnualIncome;
    // 대출 계산 결과 DTI (소수 넷째 자리)
    private BigDecimal dti;
    // NONE / RENT / OWN
    private String houseOwnership;
    private boolean firstHome;
    private boolean newlywed;
    private boolean newbornWithin2Years;
    private boolean dualIncome;
    private int childCount;
    private String region;

    // 헤더(X-Trace-Id)로만 보냄
    @JsonIgnore
    private String traceId;
}
