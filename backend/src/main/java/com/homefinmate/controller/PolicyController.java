package com.homefinmate.controller;

import com.homefinmate.common.ApiResponse;
import com.homefinmate.common.TraceIds;
import com.homefinmate.dto.policy.PolicyRecommendationRequest;
import com.homefinmate.dto.policy.PolicyRecommendationResponse;
import com.homefinmate.service.PolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// 정책 API (/api/policy): 추천, 목록 — 로그인 불필요
@RestController
@RequestMapping("/api/policy")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;

    // 정책 추천 (점수 높은 순 상위 3개)
    @PostMapping("/recommend")
    public ResponseEntity<ApiResponse<PolicyRecommendationResponse>> recommend(
            @RequestBody PolicyRecommendationRequest request) {
        PolicyRecommendationResponse response = policyService.recommend(request);
        return ResponseEntity.ok(ApiResponse.success(response, TraceIds.current()));
    }
}
