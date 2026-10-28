package com.homefinmate.service;

import com.homefinmate.common.BusinessException;
import com.homefinmate.common.TraceIds;
import com.homefinmate.dto.analyze.AnalyzeRequest;
import com.homefinmate.dto.application.ApplicationDtos.ApplyRequest;
import com.homefinmate.dto.application.ApplicationDtos.MyApplication;
import com.homefinmate.dto.application.ApplicationDtos.Receipt;
import com.homefinmate.dto.application.FinFlowApplicationRequest;
import com.homefinmate.dto.loan.LoanCalculationResponse;
import com.homefinmate.dto.policy.PolicyProduct;
import com.homefinmate.dto.policy.PolicyRecommendationResponse;
import com.homefinmate.entity.UserApplication;
import com.homefinmate.integration.FinFlowClient;
import com.homefinmate.integration.FinFlowClient.FinFlowException;
import com.homefinmate.metrics.BusinessMetrics;
import com.homefinmate.repository.LoanSimulationRepository;
import com.homefinmate.repository.UserApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Pattern;

// 대출 신청 (FinFlow 접수·조회·취소, HomeFin은 신청 번호만 보관)
@Slf4j
@Service
@RequiredArgsConstructor
public class LoanApplicationService {

    // FinFlow와 같은 규칙 (영문·숫자·-, 8~64자, UUID 권장)
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9-]{8,64}");

    private final LoanService loanService;
    private final PolicyService policyService;
    private final FinFlowClient finFlowClient;
    private final UserApplicationRepository applicationRepository;
    private final LoanSimulationRepository simulationRepository;
    private final AuditService auditService;
    private final BusinessMetrics metrics;

    // 신청 접수 (같은 키로 다시 보내면 처음 신청을 그대로 돌려줌)
    public Receipt apply(ApplyRequest request, String idempotencyKey, Long userId) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Idempotency-Key header must be 8 to 64 letters, digits or -");
        }
        // 다른 사용자의 계산 기록이면 없는 기록과 같은 404
        if (request.simulationId() != null
                && !simulationRepository.existsByIdAndUserId(request.simulationId(), userId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Simulation not found");
        }
        String traceId = TraceIds.current();
        AnalyzeRequest a = request.analysis();
        a.setTraceId(traceId);

        // 서버가 다시 계산·판정 (화면이 보낸 DTI·판정은 받지 않음)
        LoanCalculationResponse loan = loanService.compute(PolicyLoanInputs.loanRequest(a));
        PolicyRecommendationResponse policy = policyService
                .recommend(PolicyLoanInputs.policyRequest(a, loan.getDti()));
        boolean eligible = policy.getRecommendedPolicies().stream()
                .anyMatch(p -> request.productCode().equals(p.getCode())
                        && p.getEligibility() == PolicyProduct.Eligibility.ELIGIBLE);
        if (!eligible) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Product is not eligible for these inputs");
        }
        List<PolicyLoanInputs.Field> missing = PolicyLoanInputs.missing(a);
        if (!missing.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Missing inputs for application: " + PolicyLoanInputs.Field.apiNames(missing));
        }
        BigDecimal dti = loan.getDti();
        PolicyLoanInputs.Checked checked = PolicyLoanInputs.check(a, dti);
        if (!checked.outOfRange().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Inputs out of range for application: " + PolicyLoanInputs.Field.apiNames(checked.outOfRange()));
        }

        FinFlowApplicationRequest body = new FinFlowApplicationRequest(request.productCode(), "homefin:" + userId,
                checked.requestedAmount(), a.getTermMonths(), checked.housePrice(),
                checked.householdAnnualIncome(), dti, Boolean.TRUE.equals(a.getFirstHome()),
                Boolean.TRUE.equals(a.getNewlywed()), a.getNewbornWithin2Years(), a.getChildCount(),
                a.getPropertyType(), a.getDualIncome(), PolicyLoanInputs.region(a), request.simulationId());

        FinFlowClient.Received received;
        try {
            received = finFlowClient.receive(body, idempotencyKey, traceId);
        } catch (FinFlowException e) {
            // 업무 지표: FinFlow 실패 종류 (UNAVAILABLE / CONFLICT / NOT_FOUND / REJECTED)
            metrics.application(request.productCode(), e.getFailure().name());
            throw toBusiness(e);
        }
        metrics.application(request.productCode(),
                received.replayed() ? BusinessMetrics.REPLAYED : BusinessMetrics.RECEIVED);
        saveOnce(userId, received.applicationId(), request.productCode(), request.simulationId(), traceId);
        if (!received.replayed()) {
            auditService.logActivity(userId, "LOAN_APPLICATION", traceId);
        }
        // 로그에는 신청 번호·상품만 (금액·소득 없음)
        log.info("[{}] Loan application userId={} applicationId={} product={} replayed={}", traceId, userId,
                received.applicationId(), request.productCode(), received.replayed());
        return new Receipt(received.applicationId(), request.productCode(), received.status(),
                received.receivedAt(), received.replayed());
    }

    // 내 신청 최신 20건 (상태는 FinFlow에서, 한 건을 못 가져와도 목록은 보여 줌)
    public List<MyApplication> list(Long userId) {
        String traceId = TraceIds.current();
        return applicationRepository.findTop20ByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(row -> {
                    try {
                        return MyApplication.of(finFlowClient.find(row.getApplicationId(), "homefin:" + userId,
                                traceId));
                    } catch (FinFlowException e) {
                        return MyApplication.unavailable(row.getApplicationId(), row.getProductCode());
                    }
                })
                .toList();
    }

    // 내 신청 취소 (다른 사용자의 신청 번호면 FinFlow를 부르지 않고 404)
    public MyApplication cancel(String applicationId, Long userId) {
        applicationRepository.findByApplicationIdAndUserId(applicationId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Application not found"));
        String traceId = TraceIds.current();
        try {
            MyApplication result = MyApplication.of(finFlowClient.cancel(applicationId, "homefin:" + userId, traceId));
            auditService.logActivity(userId, "LOAN_APPLICATION_CANCEL", traceId);
            return result;
        } catch (FinFlowException e) {
            throw toBusiness(e);
        }
    }

    // 같은 신청 번호는 한 줄만 (재전송이 동시에 와서 둘 다 저장하려 하면 unique 제약이 막음)
    private void saveOnce(Long userId, String applicationId, String productCode, Long simulationId, String traceId) {
        if (applicationRepository.existsByApplicationId(applicationId)) {
            return;
        }
        try {
            applicationRepository.save(UserApplication.builder()
                    .userId(userId)
                    .applicationId(applicationId)
                    .productCode(productCode)
                    .simulationId(simulationId)
                    .traceId(traceId)
                    .createdAt(LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS))
                    .build());
        } catch (DataIntegrityViolationException e) {
            log.info("[{}] Application number already saved applicationId={}", traceId, applicationId);
        }
    }

    // FinFlow 실패 → 화면 응답 (응답 없음 503, 409는 FinFlow 메시지 그대로, 없는 신청 404, 그 밖의 거절 502)
    private static BusinessException toBusiness(FinFlowException e) {
        return switch (e.getFailure()) {
            case UNAVAILABLE ->
                new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "Application service unavailable");
            case CONFLICT -> new BusinessException(HttpStatus.CONFLICT, e.getMessage());
            case NOT_FOUND -> new BusinessException(HttpStatus.NOT_FOUND, "Application not found");
            case REJECTED -> new BusinessException(HttpStatus.BAD_GATEWAY, "Application service rejected the request");
        };
    }
}
