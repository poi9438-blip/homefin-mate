package com.homefinmate.controller;

import com.homefinmate.common.ApiResponse;
import com.homefinmate.common.TraceIds;
import com.homefinmate.dto.loan.DtiRequest;
import com.homefinmate.dto.loan.DtiResponse;
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
        return ResponseEntity.ok(ApiResponse.success(response, TraceIds.current()));
    }

    // DTI 단독 계산 (로그인 불필요, 저장 안 함)
    @PostMapping("/dti")
    public ResponseEntity<ApiResponse<DtiResponse>> calculateDti(
            @Valid @RequestBody DtiRequest request) {
        DtiResponse response = loanService.calculateDti(request);
        return ResponseEntity.ok(ApiResponse.success(response, TraceIds.current()));
    }

    // 내 계산 이력 (최신순)
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<LoanCalculationResponse>>> history(
            @AuthenticationPrincipal String email) {
        List<LoanCalculationResponse> history = loanService.getHistory(email);
        return ResponseEntity.ok(ApiResponse.success(history, TraceIds.current()));
    }
}
