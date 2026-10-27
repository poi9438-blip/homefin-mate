package com.homefinmate.controller;

import com.homefinmate.common.ApiResponse;
import com.homefinmate.common.TraceIds;
import com.homefinmate.dto.application.ApplicationDtos.ApplyRequest;
import com.homefinmate.dto.application.ApplicationDtos.MyApplication;
import com.homefinmate.dto.application.ApplicationDtos.Receipt;
import com.homefinmate.security.AuthUser;
import com.homefinmate.service.LoanApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 대출 신청 API (로그인 필요): 신청은 FinFlow에 접수, 내 신청 목록·취소
@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final LoanApplicationService applicationService;

    // 신청 (처음이면 201, 같은 Idempotency-Key로 다시 보낸 것이면 200 + 처음 신청)
    @PostMapping
    public ResponseEntity<ApiResponse<Receipt>> apply(@Valid @RequestBody ApplyRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal AuthUser user) {
        Receipt receipt = applicationService.apply(request, idempotencyKey, user.id());
        return ResponseEntity.status(receipt.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(ApiResponse.success(receipt, TraceIds.current()));
    }

    // 내 신청 최신 20건 (상태는 FinFlow에서)
    @GetMapping
    public ResponseEntity<ApiResponse<List<MyApplication>>> list(@AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok(ApiResponse.success(applicationService.list(user.id()), TraceIds.current()));
    }

    // 내 신청 취소
    @PostMapping("/{applicationId}/cancel")
    public ResponseEntity<ApiResponse<MyApplication>> cancel(@PathVariable String applicationId,
            @AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok(ApiResponse.success(applicationService.cancel(applicationId, user.id()),
                TraceIds.current()));
    }
}
