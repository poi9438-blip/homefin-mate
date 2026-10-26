package com.homefinmate.repository;

import com.homefinmate.entity.LoanSimulation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

// 대출 계산 결과 저장/조회
@Repository
public interface LoanSimulationRepository extends JpaRepository<LoanSimulation, Long> {

    // 사용자의 계산 이력 (최신순)
    List<LoanSimulation> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 사용자의 계산 이력 한 페이지 (최신순, 같은 시각이면 id 역순, LIMIT·OFFSET)
    List<LoanSimulation> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    // 사용자의 계산 수 (SELECT COUNT)
    long countByUserId(Long userId);

    // 사용자의 최근 계산 5건 (최신순, LIMIT 5)
    List<LoanSimulation> findTop5ByUserIdOrderByCreatedAtDesc(Long userId);

}
