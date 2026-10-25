package com.homefinmate.service;

import com.homefinmate.repository.AuditLogRepository;
import com.homefinmate.repository.LoginFailureLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

// 보관 기간 정리: 로그인 실패는 90일, 감사 기록은 365일 전보다 오래된 것 삭제
@ExtendWith(MockitoExtension.class)
class RetentionServiceTest {

    @Mock
    LoginFailureLogRepository loginFailureLogRepository;
    @Mock
    AuditLogRepository auditLogRepository;
    @InjectMocks
    RetentionService retentionService;

    @Test
    @DisplayName("[TC-DATA-014] 보관 기간 정리 기준: 로그인 실패 90일 전, 감사 기록 365일 전 (#33)")
    void purge_usesConfiguredRetention() {
        ReflectionTestUtils.setField(retentionService, "loginFailureDays", 90);
        ReflectionTestUtils.setField(retentionService, "auditDays", 365);
        LocalDateTime now = LocalDateTime.now();

        retentionService.purgeExpired();

        ArgumentCaptor<LocalDateTime> failureCut = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> auditCut = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(loginFailureLogRepository).deleteByCreatedAtBefore(failureCut.capture());
        verify(auditLogRepository).deleteByCreatedAtBefore(auditCut.capture());
        assertThat(failureCut.getValue()).isBetween(now.minusDays(90).minusSeconds(5),
                now.minusDays(90).plusSeconds(5));
        assertThat(auditCut.getValue()).isBetween(now.minusDays(365).minusSeconds(5),
                now.minusDays(365).plusSeconds(5));
    }
}
