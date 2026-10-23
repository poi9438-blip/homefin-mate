package com.homefinmate.service;

import com.homefinmate.dto.loan.DtiRequest;
import com.homefinmate.dto.loan.DtiResponse;
import com.homefinmate.dto.loan.LoanCalculationRequest;
import com.homefinmate.dto.loan.LoanCalculationResponse;
import com.homefinmate.entity.LoanSimulation;
import com.homefinmate.repository.LoanSimulationRepository;
import com.homefinmate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

// 대출 계산 (원리금균등 월 상환액, 총이자, DTI) 후 결과 저장
@Service
@RequiredArgsConstructor
@Slf4j
public class LoanService {

        private final LoanSimulationRepository loanSimulationRepository;
        private final UserRepository userRepository;
        private final AuditService auditService;

        // 대출 계산 API: 계산 => 저장(계산 기록 + 이용 기록을 한 트랜잭션으로) => 응답
        @Transactional
        public LoanCalculationResponse calculate(LoanCalculationRequest request, String email) {
                LoanCalculationResponse result = compute(request);

                // 토큰 이메일 => 사용자 id
                Long userId = userRepository.findByEmail(email)
                                .map(u -> u.getId())
                                .orElse(null);

                result.setSimulationId(saveSimulation(userId, request, result));
                // 대출 계산 이용 기록
                auditService.logActivity(userId, "LOAN_CALCULATION", request.getTraceId());
                return result;
        }

        // 계산만 (월 상환액/총이자/DTI, 저장 안 함, simulationId 없음)
        public LoanCalculationResponse compute(LoanCalculationRequest request) {
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

                // DTI = (월 상환액 + 기존 대출 월 이자) / 월 소득 (소득 없으면 0)
                BigDecimal dti = DtiRule.calculate(monthlyPayment, request.getExistingMonthlyInterest(),
                                request.getMonthlyIncome());

                log.info("[{}] Loan calculated: loanAmount={} monthlyPayment={} dti={}",
                                request.getTraceId(), request.getLoanAmount(), monthlyPayment, dti);

                // 응답 (총상환액 = 월 상환액 * 개월 수, DTI 0.60 이하면 ACCEPTABLE)
                return LoanCalculationResponse.builder()
                                .loanAmount(request.getLoanAmount())
                                .monthlyPayment(monthlyPayment)
                                .totalInterest(totalInterest)
                                .totalPayment(monthlyPayment.multiply(BigDecimal.valueOf(request.getTermMonths())))
                                .dti(dti)
                                .dtiStatus(DtiRule.status(dti))
                                .dtiBasis(DtiRule.WITH_EXISTING_INTEREST)
                                .region(request.getRegion())
                                .traceId(request.getTraceId())
                                .build();
        }

        // 계산 결과 저장 (총상환액, 판정은 저장하지 않음), 저장된 id 반환
        public Long saveSimulation(Long userId, LoanCalculationRequest request, LoanCalculationResponse result) {
                LoanSimulation simulation = loanSimulationRepository.save(LoanSimulation.builder()
                                .userId(userId)
                                .loanAmount(request.getLoanAmount())
                                .interestRate(request.getAnnualInterestRate())
                                .monthlyPayment(result.getMonthlyPayment())
                                .totalInterest(result.getTotalInterest())
                                .dti(result.getDti())
                                .dtiBasis(result.getDtiBasis())
                                .termMonths(request.getTermMonths())
                                .region(request.getRegion())
                                .traceId(request.getTraceId())
                                .build());
                return simulation.getId();
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
                                                .dti(s.getDti())
                                                .dtiStatus(DtiRule.status(s.getDti()))
                                                .dtiBasis(s.getDtiBasis())
                                                .region(s.getRegion())
                                                .traceId(s.getTraceId())
                                                .build())
                                .toList();
        }

        // DTI 단독 계산 (저장 안 함, 소득 없으면 0)
        public DtiResponse calculateDti(DtiRequest request) {
                BigDecimal dti = DtiRule.calculate(request.getMonthlyPayment(),
                                request.getExistingMonthlyInterest(), request.getMonthlyIncome());

                return DtiResponse.builder()
                                .dti(dti)
                                .dtiStatus(DtiRule.status(dti))
                                .traceId(request.getTraceId())
                                .build();
        }
}
