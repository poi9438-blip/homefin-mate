package com.homefinmate.repository;

import com.homefinmate.entity.LoginFailureLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

// 로그인 실패 기록 저장/조회
@Repository
public interface LoginFailureLogRepository extends JpaRepository<LoginFailureLog, Long> {
    // 이메일별 실패 기록 조회
    List<LoginFailureLog> findByEmail(String email);

    // 최신 100건 (DB에서 100건만 조회)
    List<LoginFailureLog> findTop100ByOrderByCreatedAtDesc();
}
