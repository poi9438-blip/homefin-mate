package com.homefinmate.repository;

import com.homefinmate.entity.LoginFailureLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

// 로그인 실패 기록 저장/조회
@Repository
public interface LoginFailureLogRepository extends JpaRepository<LoginFailureLog, Long> {
    // 이메일별 실패 기록 조회
    List<LoginFailureLog> findByEmail(String email);

    // 이메일의 최근 실패 수 (지정한 이유만, after 이후)
    long countByEmailAndReasonInAndCreatedAtAfter(String email, Collection<String> reasons, LocalDateTime after);

    // 최신 100건 (DB에서 100건만 조회)
    List<LoginFailureLog> findTop100ByOrderByCreatedAtDesc();
}
