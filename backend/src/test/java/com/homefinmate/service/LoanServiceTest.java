package com.homefinmate.service;

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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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

    // 3억, 4.5%, 30년, 월 소득 400만 => 월 1,520,055.93원, DSR 0.38
    @Test
    @DisplayName("원리금균등 월 상환액, 총이자, 총상환액, DSR")
    void calculate_standardAmortization() {
        LoanCalculationResponse res = loanService.calculate(
                request("300000000", "4.5", 360, "4000000"), "user@test.com");

        assertThat(res.getMonthlyPayment()).isEqualByComparingTo("1520055.93");
        assertThat(res.getTotalInterest()).isEqualByComparingTo("247220134.80");
        assertThat(res.getTotalPayment()).isEqualByComparingTo("547220134.80");
        assertThat(res.getDsr()).isEqualByComparingTo("0.3800");
        assertThat(res.getDsrStatus()).isEqualTo("ACCEPTABLE");
        assertThat(res.getSimulationId()).isEqualTo(100L);
        assertThat(res.getTraceId()).isEqualTo("trace-loan");
    }

    // 같은 대출, 월 소득 300만 => DSR 0.5067 > 0.43 => HIGH
    @Test
    @DisplayName("DSR이 0.43을 넘으면 HIGH")
    void calculate_dsrAboveThreshold_isHigh() {
        LoanCalculationResponse res = loanService.calculate(
                request("300000000", "4.5", 360, "3000000"), "user@test.com");

        assertThat(res.getDsr()).isEqualByComparingTo("0.5067");
        assertThat(res.getDsrStatus()).isEqualTo("HIGH");
    }

    // 1.2억, 3%, 10년 => 월 1,158,728.94원 (다른 조건에서도 공식이 맞는지)
    @Test
    @DisplayName("다른 조건(1.2억, 3%, 120개월)도 공식과 일치")
    void calculate_anotherCase() {
        LoanCalculationResponse res = loanService.calculate(
                request("120000000", "3.0", 120, "5000000"), "user@test.com");

        assertThat(res.getMonthlyPayment()).isEqualByComparingTo("1158728.94");
        assertThat(res.getDsr()).isEqualByComparingTo("0.2317");
    }

    // 저장 내용: 토큰 이메일의 사용자 id, 입력값, 계산값
    @Test
    @DisplayName("토큰 이메일의 사용자 id와 계산값으로 저장")
    void calculate_savesSimulation() {
        loanService.calculate(request("300000000", "4.5", 360, "4000000"), "user@test.com");

        ArgumentCaptor<LoanSimulation> captor = ArgumentCaptor.forClass(LoanSimulation.class);
        verify(loanSimulationRepository).save(captor.capture());
        LoanSimulation saved = captor.getValue();

        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getInterestRate()).isEqualByComparingTo("4.5");
        assertThat(saved.getMonthlyPayment()).isEqualByComparingTo("1520055.93");
        assertThat(saved.getDsr()).isEqualByComparingTo("0.3800");
        assertThat(saved.getTermMonths()).isEqualTo(360);
        assertThat(saved.getTraceId()).isEqualTo("trace-loan");
    }
}
