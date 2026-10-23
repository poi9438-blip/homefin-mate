package com.homefinmate.service;

import com.homefinmate.dto.loan.DtiRequest;
import com.homefinmate.dto.loan.DtiResponse;
import com.homefinmate.dto.loan.LoanCalculationRequest;
import com.homefinmate.dto.loan.LoanCalculationResponse;
import com.homefinmate.entity.LoanSimulation;
import com.homefinmate.entity.User;
import com.homefinmate.repository.LoanSimulationRepository;
import com.homefinmate.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// LoanService 계산 결과 검증
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoanServiceTest {

    @Mock
    LoanSimulationRepository loanSimulationRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    AuditService auditService;
    @InjectMocks
    LoanService loanService;

    @BeforeEach
    void setUp() {
        User user = User.builder().id(7L).email("user@test.com").build();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(loanSimulationRepository.save(any())).thenAnswer(inv -> {
            LoanSimulation sim = inv.getArgument(0);
            sim.setId(100L);
            return sim;
        });
    }

    private LoanCalculationRequest request(String amount, String rate, int months, String income) {
        LoanCalculationRequest req = new LoanCalculationRequest();
        req.setLoanAmount(new BigDecimal(amount));
        req.setAnnualInterestRate(new BigDecimal(rate));
        req.setTermMonths(months);
        req.setMonthlyIncome(income == null ? null : new BigDecimal(income));
        req.setTraceId("trace-loan");
        return req;
    }

    // 3억, 4.5%, 30년, 월 소득 400만 => 월 1,520,055.93원, DTI 0.38 (기존 대출 이자 없음)
    @Test
    @DisplayName("[TC-LOAN-001] 원리금균등 월 상환액, 총이자, 총상환액, DTI")
    void calculate_standardAmortization() {
        LoanCalculationResponse res = loanService.calculate(
                request("300000000", "4.5", 360, "4000000"), "user@test.com");

        assertThat(res.getMonthlyPayment()).isEqualByComparingTo("1520055.93");
        assertThat(res.getTotalInterest()).isEqualByComparingTo("247220134.80");
        assertThat(res.getTotalPayment()).isEqualByComparingTo("547220134.80");
        assertThat(res.getDti()).isEqualByComparingTo("0.3800");
        assertThat(res.getDtiStatus()).isEqualTo("ACCEPTABLE");
        assertThat(res.getDtiBasis()).isEqualTo(DtiRule.WITH_EXISTING_INTEREST);
        assertThat(res.getSimulationId()).isEqualTo(100L);
        assertThat(res.getTraceId()).isEqualTo("trace-loan");
    }

    // 경계값: 기간 1개월
    @Test
    @DisplayName("[TC-LOAN-201] 기간 1개월이면 원금 + 한 달 이자")
    void calculate_singleMonth() {
        LoanCalculationResponse res = loanService.calculate(
                request("1000000", "12", 1, null), "user@test.com");

        assertThat(res.getMonthlyPayment()).isEqualByComparingTo("1010000.00");
        assertThat(res.getTotalInterest()).isEqualByComparingTo("10000.00");
    }

    // 표준 공식(독립 계산)과 1원 이내 일치 + 총상환액·총이자 관계
    @ParameterizedTest(name = "{0}원, {1}%, {2}개월")
    @CsvSource({
            "300000000, 4.5, 360",
            "120000000, 3.0, 120",
            "50000000, 3.7, 600",
            "1000000, 0.1, 12",
            "999999999, 7.9, 240"
    })
    @DisplayName("[TC-LOAN-202] 월 상환액이 표준 공식과 1원 이내, 총상환액·총이자 관계 일치")
    void calculate_matchesIndependentFormula(String amount, String rate, int months) {
        LoanCalculationResponse res = loanService.calculate(request(amount, rate, months, null), "user@test.com");

        double p = Double.parseDouble(amount);
        double r = Double.parseDouble(rate) / 1200;
        double expected = p * r / (1 - Math.pow(1 + r, -months));

        assertThat(res.getMonthlyPayment().doubleValue()).isCloseTo(expected, within(1.0));
        assertThat(res.getTotalPayment())
                .isEqualByComparingTo(res.getMonthlyPayment().multiply(BigDecimal.valueOf(months)));
        assertThat(res.getTotalInterest())
                .isEqualByComparingTo(res.getTotalPayment().subtract(new BigDecimal(amount)));
    }

    // compute()는 계산만 하고 저장·이용 기록을 남기지 않음
    @Test
    @DisplayName("[TC-LOAN-203] 계산만 하는 compute()는 저장·이용 기록 없이 같은 계산값 반환")
    void compute_doesNotSave() {
        LoanCalculationResponse res = loanService.compute(request("300000000", "4.5", 360, "4000000"));

        assertThat(res.getMonthlyPayment()).isEqualByComparingTo("1520055.93");
        assertThat(res.getDti()).isEqualByComparingTo("0.3800");
        assertThat(res.getSimulationId()).isNull();
        org.mockito.Mockito.verifyNoInteractions(loanSimulationRepository, auditService);
    }

    // 같은 대출, 월 소득 250만 => DTI 0.6080 > 0.60 => HIGH
    @Test
    @DisplayName("[TC-LOAN-002] DTI가 0.60을 넘으면 HIGH")
    void calculate_dtiAboveThreshold_isHigh() {
        LoanCalculationResponse res = loanService.calculate(
                request("300000000", "4.5", 360, "2500000"), "user@test.com");

        assertThat(res.getDti()).isEqualByComparingTo("0.6080");
        assertThat(res.getDtiStatus()).isEqualTo("HIGH");
    }

    // 월 상환 1,520,055.93 + 기존 대출 월 이자 50만 = 2,020,055.93 ÷ 400만 => DTI
    // 0.5050
    @Test
    @DisplayName("[TC-LOAN-010] 기존 대출 월 이자를 넣으면 DTI에 더해짐 (0.38 → 0.5050)")
    void calculate_includesExistingMonthlyInterest() {
        LoanCalculationRequest req = request("300000000", "4.5", 360, "4000000");
        req.setExistingMonthlyInterest(new BigDecimal("500000"));

        LoanCalculationResponse res = loanService.compute(req);

        assertThat(res.getDti()).isEqualByComparingTo("0.5050");
        assertThat(res.getDtiBasis()).isEqualTo(DtiRule.WITH_EXISTING_INTEREST);
    }

    // 1.2억, 3%, 10년 => 월 1,158,728.94원 (다른 조건에서도 공식이 맞는지)
    @Test
    @DisplayName("[TC-LOAN-003] 다른 조건(1.2억, 3%, 120개월)도 공식과 일치")
    void calculate_anotherCase() {
        LoanCalculationResponse res = loanService.calculate(
                request("120000000", "3.0", 120, "5000000"), "user@test.com");

        assertThat(res.getMonthlyPayment()).isEqualByComparingTo("1158728.94");
        assertThat(res.getDti()).isEqualByComparingTo("0.2317");
    }

    // 저장 내용: 토큰 이메일의 사용자 id, 입력값, 계산값
    @Test
    @DisplayName("[TC-LOAN-004] 토큰 이메일의 사용자 id와 계산값으로 저장")
    void calculate_savesSimulation() {
        loanService.calculate(request("300000000", "4.5", 360, "4000000"), "user@test.com");

        ArgumentCaptor<LoanSimulation> captor = ArgumentCaptor.forClass(LoanSimulation.class);
        verify(loanSimulationRepository).save(captor.capture());
        LoanSimulation saved = captor.getValue();

        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getInterestRate()).isEqualByComparingTo("4.5");
        assertThat(saved.getMonthlyPayment()).isEqualByComparingTo("1520055.93");
        assertThat(saved.getDti()).isEqualByComparingTo("0.3800");
        assertThat(saved.getDtiBasis()).isEqualTo(DtiRule.WITH_EXISTING_INTEREST);
        assertThat(saved.getTermMonths()).isEqualTo(360);
        assertThat(saved.getTraceId()).isEqualTo("trace-loan");
        // 이용 기록도 남김
        verify(auditService).logActivity(7L, "LOAN_CALCULATION", "trace-loan");
    }

    // 이력: 저장된 값 + 총상환액/판정 재계산
    @Test
    @DisplayName("[TC-LOAN-005] 이력 조회는 총상환액과 판정을 다시 계산해서 돌려줌")
    void getHistory_recalculatesTotalPaymentAndStatus() {
        LoanSimulation high = LoanSimulation.builder()
                .id(2L).userId(7L).loanAmount(new BigDecimal("300000000"))
                .monthlyPayment(new BigDecimal("1520055.93")).termMonths(360)
                .dti(new BigDecimal("0.6080")).build();
        LoanSimulation ok = LoanSimulation.builder()
                .id(1L).userId(7L).loanAmount(new BigDecimal("120000000"))
                .monthlyPayment(new BigDecimal("1158728.94")).termMonths(120)
                .dti(new BigDecimal("0.2317")).dtiBasis(DtiRule.NEW_LOAN_ONLY).build();
        when(loanSimulationRepository.findByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(high, ok));

        List<LoanCalculationResponse> history = loanService.getHistory("user@test.com");

        assertThat(history).extracting(LoanCalculationResponse::getSimulationId).containsExactly(2L, 1L);
        assertThat(history.get(0).getTotalPayment()).isEqualByComparingTo("547220134.80");
        assertThat(history.get(0).getDtiStatus()).isEqualTo("HIGH");
        assertThat(history.get(1).getDtiStatus()).isEqualTo("ACCEPTABLE");
        assertThat(history.get(1).getDtiBasis()).isEqualTo(DtiRule.NEW_LOAN_ONLY);
    }

    // 없는 사용자: 빈 목록 (DB 조회 안 함)
    @Test
    @DisplayName("[TC-LOAN-006] 사용자를 못 찾으면 빈 목록")
    void getHistory_unknownEmail_emptyList() {
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThat(loanService.getHistory("ghost@test.com")).isEmpty();
        verify(loanSimulationRepository, org.mockito.Mockito.never()).findByUserIdOrderByCreatedAtDesc(any());
    }

    private DtiRequest dtiRequest(String payment, String interest, String income) {
        DtiRequest req = new DtiRequest();
        req.setMonthlyPayment(payment == null ? null : new BigDecimal(payment));
        req.setExistingMonthlyInterest(interest == null ? null : new BigDecimal(interest));
        req.setMonthlyIncome(income == null ? null : new BigDecimal(income));
        req.setTraceId("trace-dti");
        return req;
    }

    // 월 상환 152만 / 소득 400만 => DTI 0.3800, ACCEPTABLE (저장 안 함)
    @Test
    @DisplayName("[TC-LOAN-007] DTI 단독 계산: 0.60 이하면 ACCEPTABLE, 저장하지 않음")
    void calculateDti_belowThreshold() {
        DtiResponse res = loanService.calculateDti(dtiRequest("1520000", null, "4000000"));

        assertThat(res.getDti()).isEqualByComparingTo("0.3800");
        assertThat(res.getDtiStatus()).isEqualTo("ACCEPTABLE");
        assertThat(res.getTraceId()).isEqualTo("trace-dti");
        verify(loanSimulationRepository, org.mockito.Mockito.never()).save(any());
    }

    // 경계값 0.60 => ACCEPTABLE, 0.6001 => HIGH
    @Test
    @DisplayName("[TC-LOAN-008] DTI 경계값 0.60은 ACCEPTABLE, 넘으면 HIGH")
    void calculateDti_boundary() {
        assertThat(loanService.calculateDti(dtiRequest("600000", null, "1000000")).getDtiStatus())
                .isEqualTo("ACCEPTABLE");
        assertThat(loanService.calculateDti(dtiRequest("600100", null, "1000000")).getDtiStatus()).isEqualTo("HIGH");
    }

    // 소득 0 => DTI 0
    @Test
    @DisplayName("[TC-LOAN-009] ⚠️#64 소득이 0이면 DTI 0 (판단 불가인데 적정)")
    void calculateDti_zeroIncome() {
        DtiResponse res = loanService.calculateDti(dtiRequest("1000000", null, "0"));

        assertThat(res.getDti()).isEqualByComparingTo("0");
    }

    // 월 상환 100만만 보면 0.50(적정)이지만 기존 대출 월 이자 30만을 더하면 0.65(높음)
    @Test
    @DisplayName("[TC-LOAN-011] DTI 단독 계산에 기존 대출 월 이자를 더하면 판정이 바뀜 (0.50 → 0.65 HIGH)")
    void calculateDti_existingInterestChangesStatus() {
        assertThat(loanService.calculateDti(dtiRequest("1000000", null, "2000000")).getDtiStatus())
                .isEqualTo("ACCEPTABLE");

        DtiResponse res = loanService.calculateDti(dtiRequest("1000000", "300000", "2000000"));

        assertThat(res.getDti()).isEqualByComparingTo("0.6500");
        assertThat(res.getDtiStatus()).isEqualTo("HIGH");
    }
}
