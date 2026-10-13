package com.homefinmate.service;

import com.homefinmate.entity.AuditLog;
import com.homefinmate.entity.UserActivityLog;
import com.homefinmate.repository.AuditLogRepository;
import com.homefinmate.repository.UserActivityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 감사 기록 저장을 한 곳에 모은 서비스
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserActivityLogRepository userActivityLogRepository;

    // 사용자 기능 이용 기록 저장 (userId, action, traceId)
    public void logActivity(Long userId, String action, String traceId) {
        userActivityLogRepository.save(UserActivityLog.builder()
                .userId(userId)
                .action(action)
                .traceId(traceId)
                .build());
    }

    // 감사 기록 저장 (actorType, actorId, action, traceId)
    public void logAudit(String actorType, String actorId, String action, String traceId) {
        auditLogRepository.save(AuditLog.builder()
                .actorType(actorType)
                .actorId(actorId)
                .action(action)
                .traceId(traceId)
                .build());
    }
}
