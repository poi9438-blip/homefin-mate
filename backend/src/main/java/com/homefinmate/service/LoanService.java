package com.homefinmate.service;

import com.homefinmate.common.BusinessException;
import com.homefinmate.dto.loan.DtiRequest;
import com.homefinmate.dto.loan.DtiResponse;
import com.homefinmate.dto.loan.LoanCalculationRequest;
import com.homefinmate.dto.loan.LoanCalculationResponse;
import com.homefinmate.entity.LoanSimulation;
import com.homefinmate.repository.LoanSimulationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
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
        private final AuditService auditService;

        // 대출 계산 API: 계산 => 저장(계산 기록 + 이용 기록을 한 트랜잭션으로) => 응답
        @Transactional
        // userId: 로그인 필터가 확인한 사용자 id
        public LoanCalculationResponse calculate(LoanCalculationRequest request, Long userId) {
                LoanCalculationResponse result = compute(request);

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
                BigDecimal months = BigDecimal.valueOf(request.getTermMonths());

                BigDecimal monthlyPayment;
                BigDecimal totalInterest;
                if (monthlyRate.compareTo(BigDecimal.ZERO) > 0) {
                        // (1+r)^n
                        BigDecimal onePlusRpowN = BigDecimal.ONE.add(monthlyRate)
                                        .pow(request.getTermMonths());
                        // (1+r)^-n = 1 / (1+r)^n
                        BigDecimal reciprocal = BigDecimal.ONE
                                        .divide(onePlusRpowN, 10, RoundingMode.HALF_UP);
                        // M = P * r / (1 - (1+r)^-n), 원 단위 반올림 (#87)
                        monthlyPayment = request.getLoanAmount()
                                        .multiply(monthlyRate)
                                        .divide(BigDecimal.ONE.subtract(reciprocal), 0, RoundingMode.HALF_UP);
                        // 총이자 = 월 상환액 * 개월 수 - 원금
                        totalInterest = monthlyPayment.multiply(months).subtract(request.getLoanAmount());
                } else {
                        // 무이자: 월 상환액 = 원금 / 개월 수 (원 단위 반올림), 이자 0 (#34)
                        monthlyPayment = request.getLoanAmount().divide(months, 0, RoundingMode.HALF_UP);
                        totalInterest = BigDecimal.ZERO;
                }

                // DTI = (월 상환액 + 기존 대출 월 이자) / 월 소득 (소득은 요청 검증에서 필수, #64)
                BigDecimal dti = DtiRule.calculate(monthlyPayment, request.getExistingMonthlyInterest(),
                                request.getMonthlyIncome());

                // 금액·DTI는 로그에 남기지 않고 판정만 (#83)
                log.info("[{}] Loan calculated: dtiStatus={}", request.getTraceId(), DtiRule.status(dti));

                // 응답 (총상환액 = 원금 + 총이자, DTI 0.60 이하면 ACCEPTABLE)
                return LoanCalculationResponse.builder()
                                .loanAmount(request.getLoanAmount())
                                .monthlyPayment(monthlyPayment)
                                .totalInterest(totalInterest)
                                .totalPayment(request.getLoanAmount().add(totalInterest))
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

        // 이력 한 페이지의 최대 건수
        public static final int MAX_HISTORY_SIZE = 100;

        // 내 계산 이력 한 페이지 (최신순, page는 0부터, size는 1~100)
        public List<LoanCalculationResponse> getHistory(Long userId, int page, int size) {
                if (page < 0 || size < 1 || size > MAX_HISTORY_SIZE) {
                        throw new BusinessException(HttpStatus.BAD_REQUEST,
                                        "page must be 0 or more and size must be between 1 and " + MAX_HISTORY_SIZE);
                }
                return loanSimulationRepository
                                .findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(page, size)).stream()
                                .map(s -> LoanCalculationResponse.builder()
                                                .simulationId(s.getId())
                                                .loanAmount(s.getLoanAmount())
                                                .monthlyPayment(s.getMonthlyPayment())
                                                .totalInterest(s.getTotalInterest())
                                                // 총상환액 = 원금 + 총이자 (계산 응답과 같은 방식)
                                                .totalPayment(s.getTotalInterest() != null
                                                                ? s.getLoanAmount().add(s.getTotalInterest())
                                                                : BigDecimal.ZERO)
                                                .dti(s.getDti())
                                                .dtiStatus(DtiRule.status(s.getDti()))
                                                .dtiBasis(s.getDtiBasis())
                                                .region(s.getRegion())
                                                .traceId(s.getTraceId())
                                                .build())
                                .toList();
        }

        // DTI 단독 계산 (저장 안 함)
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
