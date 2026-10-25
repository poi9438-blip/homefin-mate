package com.homefinmate.controller;

import com.homefinmate.common.ApiResponse;
import com.homefinmate.dto.admin.LoginFailureSearchRequest;
import com.homefinmate.entity.AuditLog;
import com.homefinmate.entity.LoginFailureLog;
import com.homefinmate.entity.UserActivityLog;
import com.homefinmate.repository.AuditLogRepository;
import com.homefinmate.repository.LoginFailureLogRepository;
import com.homefinmate.repository.UserActivityLogRepository;
import com.homefinmate.util.EmailHasher;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 관리자 API (/api/admin, ADMIN만): 감사 기록, 로그인 실패, 이용 기록, 연동 실패
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

        private final AuditLogRepository auditLogRepository;
        private final LoginFailureLogRepository loginFailureLogRepository;
        private final UserActivityLogRepository userActivityLogRepository;
        private final EmailHasher emailHasher;

        // 감사 기록 최근 100건
        @GetMapping("/audit-logs")
        public ResponseEntity<ApiResponse<List<AuditLog>>> getAuditLogs(
                        @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {

                // DB에서 최신 100건만 조회
                List<AuditLog> logs = auditLogRepository.findTop100ByOrderByCreatedAtDesc();
                return ResponseEntity.ok(ApiResponse.success(logs, traceId));
        }

        // 로그인 실패 최근 100건 (이메일은 HMAC 값)
        @GetMapping("/login-failures")
        public ResponseEntity<ApiResponse<List<LoginFailureLog>>> getLoginFailures(
                        @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
                List<LoginFailureLog> logs = loginFailureLogRepository.findTop100ByOrderByCreatedAtDesc();
                return ResponseEntity.ok(ApiResponse.success(logs, traceId));
        }

        // 이메일로 로그인 실패 검색 (입력 이메일 → HMAC, 최근 100건)
        @PostMapping("/login-failures/search")
        public ResponseEntity<ApiResponse<List<LoginFailureLog>>> searchLoginFailures(
                        @Valid @RequestBody LoginFailureSearchRequest request,
                        @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
                List<LoginFailureLog> logs = loginFailureLogRepository
                                .findTop100ByEmailHashOrderByCreatedAtDesc(emailHasher.hash(request.getEmail()));
                return ResponseEntity.ok(ApiResponse.success(logs, traceId));
        }

        // 이용 기록 최근 100건
        @GetMapping("/user-activity")
        public ResponseEntity<ApiResponse<List<UserActivityLog>>> getUserActivity(
                        @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
                List<UserActivityLog> logs = userActivityLogRepository.findTop100ByOrderByCreatedAtDesc();
                return ResponseEntity.ok(ApiResponse.success(logs, traceId));
        }

        // RiskWatch 연동 실패 최근 100건
        @GetMapping("/integration-failures")
        public ResponseEntity<ApiResponse<List<AuditLog>>> getIntegrationFailures(
                        @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
                // 연동 실패만 DB에서 최신 100건 조회
                List<AuditLog> logs = auditLogRepository
                                .findTop100ByActionStartingWithOrderByCreatedAtDesc("RISKWATCH_INTEGRATION_FAILURE");
                return ResponseEntity.ok(ApiResponse.success(logs, traceId));
        }
}
