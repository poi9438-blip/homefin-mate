package com.homefinmate.repository;

import com.homefinmate.entity.LoginFailureLog;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 로그인 시도 제한용 실패 수 조회 검증 (이메일·이유·시간 조건)
@DataJpaTest
@ActiveProfiles("test")
class LoginFailureLogRepositoryTest {

    private static final List<String> COUNTED = List.of("User not found", "Incorrect password");

    @Autowired
    LoginFailureLogRepository loginFailureLogRepository;
    @Autowired
    EntityManager entityManager;

    private LoginFailureLog failure(String email, String reason) {
        return loginFailureLogRepository.save(LoginFailureLog.builder().email(email).reason(reason).build());
    }

    // 같은 이메일, 센 이유, 기준 시각 이후만 셈 (다른 이메일, "Too many attempts", 20분 전 기록은 제외)
    @Test
    @DisplayName("[TC-DATA-010] 최근 실패 수는 같은 이메일·센 이유(없는 이메일·비밀번호 틀림)·15분 안의 기록만 셈")
    void countRecentFailures_onlyMatchingEmailReasonAndTime() {
        failure("a@test.com", "Incorrect password");
        failure("a@test.com", "User not found");
        failure("a@test.com", "Too many attempts");
        failure("b@test.com", "Incorrect password");
        LoginFailureLog old = failure("a@test.com", "Incorrect password");
        entityManager.flush();
        entityManager.createNativeQuery("update login_failure_logs set created_at = :t where id = :id")
                .setParameter("t", LocalDateTime.now().minusMinutes(20))
                .setParameter("id", old.getId())
                .executeUpdate();

        long count = loginFailureLogRepository.countByEmailAndReasonInAndCreatedAtAfter(
                "a@test.com", COUNTED, LocalDateTime.now().minusMinutes(15));

        assertThat(count).isEqualTo(2);
    }
}
