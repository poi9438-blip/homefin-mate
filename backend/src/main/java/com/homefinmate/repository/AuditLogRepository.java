package com.homefinmate.repository;

import com.homefinmate.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// 감사 기록 저장/조회
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
