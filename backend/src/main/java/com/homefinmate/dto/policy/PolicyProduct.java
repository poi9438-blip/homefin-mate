package com.homefinmate.dto.policy;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

// 정책 상품 판정 결과 (조건별 판정, 적용 한도, 금리, 출처)
@Data
@Builder
public class PolicyProduct {

    public enum Eligibility {
        ELIGIBLE, NEEDS_CHECK, NOT_ELIGIBLE
    }

    private String name;
    // 운영 기관
    private String provider;
    private String description;
    // 종합 판정: 미충족이 하나라도 있으면 NOT_ELIGIBLE, 확인 필요가 있으면 NEEDS_CHECK, 모두 충족이면 ELIGIBLE
    private Eligibility eligibility;
    private List<PolicyCondition> conditions;
    // 입력으로 판정할 수 없어 신청 전에 따로 확인해야 하는 조건
    private List<String> additionalChecks;
    // 입력 기준 최대 대출 가능액 (상품 한도와 주택가격 × LTV 중 작은 값, 알 수 없으면 null)
    private BigDecimal maxLoanAmount;
    // 금리 안내 (공시 기준)
    private String interestRate;
    private String sourceUrl;
    // 공식 출처를 확인한 날짜 (yyyy-MM-dd)
    private String checkedDate;
}
