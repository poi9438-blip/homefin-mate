package com.homefinmate.service;

import com.homefinmate.dto.loan.LoanCalculationRequest;
import com.homefinmate.dto.loan.LoanCalculationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

//통합 분석 결과 저장 (계산 기록 + 이용 기록을 한 트랜잭션으로)
@Service
@RequiredArgsConstructor
public class AnalyzeResultSaver {

    private final LoanService loanService;
    private final AuditService auditService;

    // 저장된 계산 id 반환
    @Transactional
    public Long save(Long userId, LoanCalculationRequest loanRequest, LoanCalculationResponse loanResult) {
        Long simulationId = loanService.saveSimulation(userId, loanRequest, loanResult);
        // 통합 분석 이용 기록 1줄 (대출 계산 API의 LOAN_CALCULATION과 구분)
        auditService.logActivity(userId, "UNIFIED_ANALYSIS", loanRequest.getTraceId());
        return simulationId;
    }
}
