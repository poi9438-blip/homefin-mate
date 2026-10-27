package com.homefinmate.repository;

import com.homefinmate.entity.UserApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// 사용자의 FinFlow 신청 번호 저장/조회
@Repository
public interface UserApplicationRepository extends JpaRepository<UserApplication, Long> {

    // 내 신청 최신 20건
    List<UserApplication> findTop20ByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    // 내 신청인지 확인 (다른 사용자의 신청 번호면 비어 있음)
    Optional<UserApplication> findByApplicationIdAndUserId(String applicationId, Long userId);

    boolean existsByApplicationId(String applicationId);
}
