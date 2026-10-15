package com.homefinmate.repository;

import com.homefinmate.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

// 감사 기록 저장/조회
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    // 최신 100건 (DB에서 100건만 조회)
    List<AuditLog> findTop100ByOrderByCreatedAtDesc();

    // action이 접두사로 시작하는 최신 100건 (연동 실패 조회용)
    List<AuditLog> findTop100ByActionStartingWithOrderByCreatedAtDesc(String actionPrefix);
}
