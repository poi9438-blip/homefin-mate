package com.homefinmate.controller;

import com.homefinmate.common.ApiResponse;
import com.homefinmate.dto.analyze.AnalyzeRequest;
import com.homefinmate.dto.analyze.AnalyzeResponse;
import com.homefinmate.service.AnalyzeService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// 통합 분석 API (POST /api/analyze, 로그인 필요)
@RestController
@RequestMapping("/api/analyze")
@RequiredArgsConstructor
public class AnalyzeController {

    private final AnalyzeService analyzeService;

    // 통합 분석 실행 (입력 검증 => 4단계 분석 => 결과 반환)
    @PostMapping
    public ResponseEntity<ApiResponse<AnalyzeResponse>> analyze(
            @Valid @RequestBody AnalyzeRequest request,
            @AuthenticationPrincipal String email) {

        AnalyzeResponse result = analyzeService.analyze(request, email);
        return ResponseEntity.ok(ApiResponse.success(result, request.getTraceId()));
    }
}
