package com.homefinmate.controller;

import com.homefinmate.common.ApiResponse;
import com.homefinmate.dto.analysis.RegionAnalysisResponse;
import com.homefinmate.service.AnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// 지역 분석 API (/api/analysis/region, 로그인 불필요)
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    // 지역 분석 조회 (region 필수, traceId 헤더 선택)
    @GetMapping("/region")
    public ResponseEntity<ApiResponse<RegionAnalysisResponse>> getRegionAnalysis(
            @RequestParam String region,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        RegionAnalysisResponse response = analysisService.getRegionAnalysis(region, traceId);
        return ResponseEntity.ok(ApiResponse.success(response, traceId));
    }
}
