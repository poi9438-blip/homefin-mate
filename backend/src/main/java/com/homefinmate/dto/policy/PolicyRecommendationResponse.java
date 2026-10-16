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
    private String traceId;
}
