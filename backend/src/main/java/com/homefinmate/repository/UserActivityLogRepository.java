package com.homefinmate.repository;

import com.homefinmate.entity.UserActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

// 사용자 기능 이용 기록 저장/조회
@Repository
public interface UserActivityLogRepository extends JpaRepository<UserActivityLog, Long> {
    // 사용자별 이용 기록 (최신순)
    List<UserActivityLog> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 최신 100건 (DB에서 100건만 조회)
    List<UserActivityLog> findTop100ByOrderByCreatedAtDesc();
}
