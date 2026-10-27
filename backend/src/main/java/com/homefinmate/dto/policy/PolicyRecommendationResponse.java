package com.homefinmate.dto.policy;

import lombok.Builder;
import lombok.Data;

import java.util.List;

//  정책 판정 결과 (상품별 판정, 요약 문장, 충족 상품 수)
@Data
@Builder
public class PolicyRecommendationResponse {
    // 충족 → 확인 필요 → 미충족 순
    private List<PolicyProduct> recommendedPolicies;
    // 요약 문장
    private String summary;
    // 조건을 모두 충족한 상품 수
    private Integer eligibleCount;
    // 추천 상품 이름 (조건을 모두 충족한 상품만, 없으면 null)
    private String recommendedProduct;
    // 추천 상품 코드 (RiskWatch 판정에 씀, 없으면 null)
    private String recommendedProductCode;
    // 추천 이유 또는 추천하지 않은 이유
    private String recommendationReason;
    private String traceId;
}
