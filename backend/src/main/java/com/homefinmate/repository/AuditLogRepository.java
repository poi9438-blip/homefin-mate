package com.homefinmate.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // 보관 기간이 지난 기록 삭제 (한 번의 DELETE)
    @Modifying
    @Query("delete from AuditLog a where a.createdAt < :before")
    int deleteByCreatedAtBefore(@Param("before") java.time.LocalDateTime before);
}
