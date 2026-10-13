package com.homefinmate.controller;

import com.homefinmate.common.ApiResponse;
import com.homefinmate.dto.report.ReportResponse;
import com.homefinmate.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// 리포트 API (/api/report, 로그인 필요)
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    // 내 요약 리포트
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<ReportResponse>> getSummary(
            @AuthenticationPrincipal String email,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        ReportResponse response = reportService.generateSummary(email, traceId);
        return ResponseEntity.ok(ApiResponse.success(response, traceId));
    }

    // 리포트 생성
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<ReportResponse>> generateReport(
            @AuthenticationPrincipal String email,
            @RequestParam String reportType,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ResponseEntity.ok(ApiResponse.success(null, traceId));
    }
}
