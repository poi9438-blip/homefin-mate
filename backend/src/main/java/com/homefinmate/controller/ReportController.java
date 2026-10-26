package com.homefinmate.controller;

import com.homefinmate.common.ApiResponse;
import com.homefinmate.common.TraceIds;
import com.homefinmate.dto.report.ReportResponse;
import com.homefinmate.service.ReportService;
import com.homefinmate.security.AuthUser;
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
            @AuthenticationPrincipal AuthUser user) {
        ReportResponse response = reportService.generateSummary(user.id(), TraceIds.current());
        return ResponseEntity.ok(ApiResponse.success(response, TraceIds.current()));
    }
}
