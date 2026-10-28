package com.homefinmate.service;

import com.homefinmate.common.BusinessException;
import com.homefinmate.dto.analyze.AnalyzeRequest;
import com.homefinmate.dto.application.ApplicationDtos.ApplyRequest;
import com.homefinmate.dto.loan.LoanCalculationResponse;
import com.homefinmate.dto.policy.PolicyProduct;
import com.homefinmate.dto.policy.PolicyProduct.Eligibility;
import com.homefinmate.dto.policy.PolicyRecommendationResponse;
import com.homefinmate.integration.FinFlowClient;
import com.homefinmate.integration.FinFlowClient.FinFlowException;
import com.homefinmate.metrics.BusinessMetrics;
import com.homefinmate.repository.LoanSimulationRepository;
import com.homefinmate.repository.UserApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

// 신청 전 서버 확인: 상품 판정 다시 하기, 빠진 입력·범위 (FinFlow를 부르지 않는 경우)
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoanApplicationServiceTest {

    static final String KEY = "6f0a7c5e-9d2b-4c1a-8e3f-1b2c3d4e5f60";

    @Mock
    LoanService loanService;
    @Mock
    PolicyService policyService;
    @Mock
    FinFlowClient finFlowClient;
    @Mock
    UserApplicationRepository applicationRepository;
    @Mock
    LoanSimulationRepository simulationRepository;
    @Mock
    AuditService auditService;
    @Mock
    BusinessMetrics metrics;
    @InjectMocks
    LoanApplicationService service;

    @BeforeEach
    void setUp() {
        when(loanService.compute(any())).thenReturn(LoanCalculationResponse.builder()
                .monthlyPayment(new BigDecimal("1520056")).dti(new BigDecimal("0.3801")).build());
        policy(Eligibility.ELIGIBLE);
    }

    private void policy(Eligibility bogeumjari) {
        when(policyService.recommend(any())).thenReturn(PolicyRecommendationResponse.builder()
                .recommendedPolicies(List.of(
                        PolicyProduct.builder().code("BOGEUMJARI").eligibility(bogeumjari).build(),
                        PolicyProduct.builder().code("DIDIMDOL").eligibility(Eligibility.NOT_ELIGIBLE).build()))
                .build());
    }

    static AnalyzeRequest analysis() {
        AnalyzeRequest r = new AnalyzeRequest();
        r.setLoanAmount(new BigDecimal("300000000"));
        r.setAnnualInterestRate(new BigDecimal("4.5"));
        r.setTermMonths(360);
        r.setMonthlyIncome(new BigDecimal("4000000"));
        r.setPropertyType("NONE");
        r.setRegion("부산");
        r.setHousePrice(new BigDecimal("600000000"));
        r.setHouseholdAnnualIncome(new BigDecimal("48000000"));
        r.setFirstHome(true);
        r.setNewlywed(false);
        r.setChildCount(0);
        r.setNewbornWithin2Years(false);
        r.setDualIncome(false);
        return r;
    }

    private void assertBadRequest(ApplyRequest request, String... messageParts) {
        assertThatThrownBy(() -> service.apply(request, KEY, 7L))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    org.assertj.core.api.Assertions.assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    org.assertj.core.api.Assertions.assertThat(e.getMessage()).contains(messageParts);
                });
        verifyNoInteractions(finFlowClient, applicationRepository);
    }

    @Test
    @DisplayName("[TC-APPLY-205] 고른 상품이 다시 계산한 판정에서 충족이 아니면(확인 필요·미충족·목록에 없음) 400, FinFlow를 부르지 않음")
    void productNotEligible_badRequest() {
        for (Eligibility e : List.of(Eligibility.NEEDS_CHECK, Eligibility.NOT_ELIGIBLE)) {
            policy(e);
            assertBadRequest(new ApplyRequest("BOGEUMJARI", null, analysis()), "Product is not eligible");
        }
        policy(Eligibility.ELIGIBLE);
        // 충족 목록에 없는 상품 (DIDIMDOL은 미충족, NEWBORN_DIDIMDOL은 목록에 없음)
        assertBadRequest(new ApplyRequest("NEWBORN_DIDIMDOL", null, analysis()), "Product is not eligible");
    }

    @Test
    @DisplayName("[TC-APPLY-206] 주택 보유·2년 내 출산·입양·맞벌이 중 빠진 입력 / FinFlow 입력 범위 밖이면 400 + 항목 이름, FinFlow를 부르지 않음")
    void missingOrOutOfRange_badRequest() {
        AnalyzeRequest missing = analysis();
        missing.setNewbornWithin2Years(null);
        missing.setDualIncome(null);
        assertBadRequest(new ApplyRequest("BOGEUMJARI", null, missing), "Missing inputs", "newbornWithin2Years",
                "dualIncome");

        List<Consumer<AnalyzeRequest>> outside = List.of(
                r -> r.setTermMonths(11), r -> r.setLoanAmount(new BigDecimal("300000000.5")),
                r -> r.setChildCount(21));
        List<String> names = List.of("termMonths", "loanAmount", "childCount");
        for (int i = 0; i < outside.size(); i++) {
            AnalyzeRequest r = analysis();
            outside.get(i).accept(r);
            assertBadRequest(new ApplyRequest("BOGEUMJARI", null, r), "out of range", names.get(i));
        }
    }

    private static FinFlowException failure(FinFlowClient.Failure kind) {
        FinFlowException e = mock(FinFlowException.class);
        when(e.getFailure()).thenReturn(kind);
        return e;
    }

    // 업무 지표: FinFlow를 부른 신청만 결과별로 기록 (서버 확인에서 막힌 400은 기록 없음)
    @Test
    @DisplayName("[TC-OBS-202] 신청의 FinFlow 결과: 접수 / 같은 키 재전송 / 진행 중(409) / 응답 없음 → 지표 RECEIVED / REPLAYED / CONFLICT / UNAVAILABLE, 400은 기록 없음")
    void recordsApplicationResultMetric() {
        ApplyRequest request = new ApplyRequest("BOGEUMJARI", null, analysis());
        // 예외 mock은 아래 when(...) 사슬 밖에서 먼저 만듦 (사슬 안에서 다른 mock을 설정하면 Mockito 오류)
        FinFlowException conflict = failure(FinFlowClient.Failure.CONFLICT);
        FinFlowException unavailable = failure(FinFlowClient.Failure.UNAVAILABLE);
        when(finFlowClient.receive(any(), any(), any()))
                .thenReturn(new FinFlowClient.Received("app-1", "RECEIVED", null, false))
                .thenReturn(new FinFlowClient.Received("app-1", "RECEIVED", null, true))
                .thenThrow(conflict)
                .thenThrow(unavailable);

        service.apply(request, KEY, 7L);
        service.apply(request, KEY, 7L);
        assertThatThrownBy(() -> service.apply(request, KEY, 7L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.apply(request, KEY, 7L)).isInstanceOf(BusinessException.class);
        verify(metrics).application("BOGEUMJARI", BusinessMetrics.RECEIVED);
        verify(metrics).application("BOGEUMJARI", BusinessMetrics.REPLAYED);
        verify(metrics).application("BOGEUMJARI", "CONFLICT");
        verify(metrics).application("BOGEUMJARI", "UNAVAILABLE");

        // 서버 확인에서 막힘 (FinFlow를 부르지 않음)
        policy(PolicyProduct.Eligibility.NOT_ELIGIBLE);
        assertThatThrownBy(() -> service.apply(request, KEY, 7L)).isInstanceOf(BusinessException.class);
        verifyNoMoreInteractions(metrics);
    }
}
