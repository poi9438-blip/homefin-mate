package com.homefinmate.dto.policy;

import lombok.Builder;
import lombok.Data;

import java.util.List;

// 정책 추천 결과 (상위 3개, 요약 문장, 합계 점수)
@Data
@Builder
public class PolicyRecommendationResponse {
    // 점수 높은 순 상위 3개
    private List<PolicyProduct> recommendedPolicies;
    // 요약 문장
    private String summary;
    // 상위 3개 점수 합계
    private Integer score;
    private String traceId;
}
