package com.homefinmate.repository;

import com.homefinmate.entity.AuditLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

// 감사 기록 최신 100건 조회, 연동 실패 필터 검증
@DataJpaTest
@ActiveProfiles("test")
class AuditLogRepositoryTest {

    private static final String FAILURE = "RISKWATCH_INTEGRATION_FAILURE";

    @Autowired
    AuditLogRepository auditLogRepository;

    private AuditLog log(String action) {
        return AuditLog.builder().actorType("SYSTEM").actorId("HOMEFIT_MATE").action(action).build();
    }

    @Test
    @DisplayName("[TC-DATA-302] 감사 기록이 120건이어도 최신순 100건만, 연동 실패 조회는 연동 실패만")
    void top100_andIntegrationFailureFilter() {
        auditLogRepository.saveAll(IntStream.range(0, 90).mapToObj(i -> log("LOGIN_SUCCESS")).toList());
        auditLogRepository.saveAll(IntStream.range(0, 30).mapToObj(i -> log(FAILURE + ": test " + i)).toList());

        List<AuditLog> latest = auditLogRepository.findTop100ByOrderByCreatedAtDesc();
        List<AuditLog> failures = auditLogRepository.findTop100ByActionStartingWithOrderByCreatedAtDesc(FAILURE);

        assertThat(latest).hasSize(100)
                .isSortedAccordingTo(Comparator.comparing(AuditLog::getCreatedAt).reversed());
        assertThat(failures).hasSize(30)
                .allSatisfy(a -> assertThat(a.getAction()).startsWith(FAILURE));
    }
}
