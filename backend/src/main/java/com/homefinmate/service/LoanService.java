package com.homefinmate.service;

import com.homefinmate.dto.loan.LoanCalculationRequest;
import com.homefinmate.dto.loan.LoanCalculationResponse;
import com.homefinmate.entity.LoanSimulation;
import com.homefinmate.repository.LoanSimulationRepository;
import com.homefinmate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

// 대출 계산 (원리금균등 월 상환액, 총이자, DSR) 후 결과 저장
@Service
@RequiredArgsConstructor
@Slf4j
public class LoanService {

        private final LoanSimulationRepository loanSimulationRepository;
        private final UserRepository userRepository;
        private final AuditService auditService;

        // 대출 계산: 월 상환액/총이자/DSR 계산 => 저장 => 응답
        public LoanCalculationResponse calculate(LoanCalculationRequest request, String email) {
                // 월 이율 = 연이율(%) / 1200 (소수 10자리)
                BigDecimal monthlyRate = request.getAnnualInterestRate()
                                .divide(BigDecimal.valueOf(1200), 10, RoundingMode.HALF_UP);

                BigDecimal monthlyPayment = BigDecimal.ZERO;
                BigDecimal totalInterest = BigDecimal.ZERO;

                // 이율이 0보다 클 때만 공식 적용 (0%는 상환액 0)
                if (monthlyRate.compareTo(BigDecimal.ZERO) > 0) {
                        // (1+r)^n
                        BigDecimal onePlusRpowN = BigDecimal.ONE.add(monthlyRate)
                                        .pow(request.getTermMonths());
                        // (1+r)^-n = 1 / (1+r)^n
                        BigDecimal reciprocal = BigDecimal.ONE
                                        .divide(onePlusRpowN, 10, RoundingMode.HALF_UP);
                        // M = P * r / (1 - (1+r)^-n)
                        monthlyPayment = request.getLoanAmount()
                                        .multiply(monthlyRate)
                                        .divide(BigDecimal.ONE.subtract(reciprocal), 2, RoundingMode.HALF_UP);
                        // 총이자 = 월 상환액 * 개월 수 - 원금
                        totalInterest = monthlyPayment
                                        .multiply(BigDecimal.valueOf(request.getTermMonths()))
                                        .subtract(request.getLoanAmount());
                }

                // DSR = 월 상환액 / 월 소득 (소득 없으면 0)
                BigDecimal dsr = BigDecimal.ZERO;
                if (request.getMonthlyIncome() != null && request.getMonthlyIncome().compareTo(BigDecimal.ZERO) > 0) {
                        dsr = monthlyPayment.divide(request.getMonthlyIncome(), 4, RoundingMode.HALF_UP);
                }

                // 토큰 이메일 => 사용자 id
                Long userId = userRepository.findByEmail(email)
                                .map(u -> u.getId())
                                .orElse(null);

                // 계산 결과 저장 (총상환액, 판정은 저장하지 않음)
                LoanSimulation simulation = loanSimulationRepository.save(LoanSimulation.builder()
                                .userId(userId)
                                .loanAmount(request.getLoanAmount())
                                .interestRate(request.getAnnualInterestRate())
                                .monthlyPayment(monthlyPayment)
                                .totalInterest(totalInterest)
                                .dsr(dsr)
                                .termMonths(request.getTermMonths())
                                .region(request.getRegion())
                                .traceId(request.getTraceId())
                                .build());

                // 대출 계산 이용 기록
                auditService.logActivity(userId, "LOAN_CALCULATION", request.getTraceId());

                log.info("[{}] Loan calculated: loanAmount={} monthlyPayment={} dsr={}",
                                request.getTraceId(), request.getLoanAmount(), monthlyPayment, dsr);

                // 응답 (총상환액 = 월 상환액 * 개월 수, DSR 0.43 이하면 ACCEPTABLE)
                return LoanCalculationResponse.builder()
                                .simulationId(simulation.getId())
                                .loanAmount(request.getLoanAmount())
                                .monthlyPayment(monthlyPayment)
                                .totalInterest(totalInterest)
                                .totalPayment(monthlyPayment.multiply(BigDecimal.valueOf(request.getTermMonths())))
                                .dsr(dsr)
                                .dsrStatus(dsr.compareTo(BigDecimal.valueOf(0.43)) <= 0 ? "ACCEPTABLE" : "HIGH")
                                .region(request.getRegion())
                                .traceId(request.getTraceId())
                                .build();
        }

        // 내 계산 이력 (최신순, 사용자를 못 찾으면 빈 목록)
        public List<LoanCalculationResponse> getHistory(String email) {
                Long userId = userRepository.findByEmail(email).map(u -> u.getId()).orElse(null);
                if (userId == null)
                        return List.of();
                return loanSimulationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                                .map(s -> LoanCalculationResponse.builder()
                                                .simulationId(s.getId())
                                                .loanAmount(s.getLoanAmount())
                                                .monthlyPayment(s.getMonthlyPayment())
                                                .totalInterest(s.getTotalInterest())
                                                .totalPayment(s.getMonthlyPayment() != null && s.getTermMonths() != null
                                                                ? s.getMonthlyPayment().multiply(
                                                                                BigDecimal.valueOf(s.getTermMonths()))
                                                                : BigDecimal.ZERO)
                                                .dsr(s.getDsr())
                                                .dsrStatus(s.getDsr() != null
                                                                && s.getDsr().compareTo(BigDecimal.valueOf(0.43)) <= 0
                                                                                ? "ACCEPTABLE"
                                                                                : "HIGH")
                                                .region(s.getRegion())
                                                .traceId(s.getTraceId())
                                                .build())
                                .toList();
        }
}
