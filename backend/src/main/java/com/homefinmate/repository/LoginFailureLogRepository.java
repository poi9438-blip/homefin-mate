package com.homefinmate.repository;

import com.homefinmate.entity.LoginFailureLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

// 로그인 실패 기록 저장/조회
@Repository
public interface LoginFailureLogRepository extends JpaRepository<LoginFailureLog, Long> {
    // 이메일의 최근 실패 수 (지정한 이유만, after 이후)
    long countByEmailHashAndReasonInAndCreatedAtAfter(String emailHash, Collection<String> reasons,
            LocalDateTime after);

    // 보관 기간이 지난 기록 삭제 (한 번의 DELETE)
    @Modifying
    @Query("delete from LoginFailureLog l where l.createdAt < :before")
    int deleteByCreatedAtBefore(@Param("before") LocalDateTime before);

    // 최신 100건 (DB에서 100건만 조회)
    List<LoginFailureLog> findTop100ByOrderByCreatedAtDesc();

    // 한 이메일(HMAC)의 최신 100건 (관리자 검색, #89)
    List<LoginFailureLog> findTop100ByEmailHashOrderByCreatedAtDesc(String emailHash);
}
