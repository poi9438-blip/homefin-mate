package com.homefinmate.service;

import com.homefinmate.repository.AuditLogRepository;
import com.homefinmate.repository.LoginFailureLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// 보관 기간이 지난 기록 삭제 (로그인 실패 90일, 감사 기록 365일, 매일 03:00)
@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionService {

    private final LoginFailureLogRepository loginFailureLogRepository;
    private final AuditLogRepository auditLogRepository;

    @Value("${app.retention.login-failure-days:90}")
    private int loginFailureDays;

    @Value("${app.retention.audit-days:365}")
    private int auditDays;

    @Scheduled(cron = "${app.retention.cron:0 0 3 * * *}")
    @Transactional
    public void purgeExpired() {
        LocalDateTime now = LocalDateTime.now();
        int failures = loginFailureLogRepository.deleteByCreatedAtBefore(now.minusDays(loginFailureDays));
        int audits = auditLogRepository.deleteByCreatedAtBefore(now.minusDays(auditDays));
        log.info("Retention purge: loginFailures={} auditLogs={}", failures, audits);
    }
}
