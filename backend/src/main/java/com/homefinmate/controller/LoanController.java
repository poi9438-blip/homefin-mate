package com.homefinmate.controller;

import com.homefinmate.common.ApiResponse;
import com.homefinmate.dto.loan.LoanCalculationRequest;
import com.homefinmate.dto.loan.LoanCalculationResponse;
import com.homefinmate.service.LoanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 대출 API (/api/loan): 계산, 내 계산 이력 (로그인 필요)
@RestController
@RequestMapping("/api/loan")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService loanService;

    // 대출 계산 (결과 저장)
    @PostMapping("/calculate")
    public ResponseEntity<ApiResponse<LoanCalculationResponse>> calculate(
            @Valid @RequestBody LoanCalculationRequest request,
            @AuthenticationPrincipal String email) {
        LoanCalculationResponse response = loanService.calculate(request, email);
        return ResponseEntity.ok(ApiResponse.success(response, request.getTraceId()));
    }

    // 내 계산 이력 (최신순)
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<LoanCalculationResponse>>> history(
            @AuthenticationPrincipal String email,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        List<LoanCalculationResponse> history = loanService.getHistory(email);
        return ResponseEntity.ok(ApiResponse.success(history, traceId));
    }
}
