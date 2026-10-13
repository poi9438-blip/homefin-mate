package com.homefinmate.repository;

import com.homefinmate.entity.LoanSimulation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// 대출 계산 결과 저장/조회
@Repository
public interface LoanSimulationRepository extends JpaRepository<LoanSimulation, Long> {

    // 사용자의 계산 이력 (최신순)
    List<LoanSimulation> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 사용자의 가장 최근 계산 (1건)
    Optional<LoanSimulation> findTopByUserIdOrderByCreatedAtDesc(Long userId);
}
