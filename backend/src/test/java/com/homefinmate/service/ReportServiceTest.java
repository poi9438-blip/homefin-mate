package com.homefinmate.service;

import com.homefinmate.dto.report.ReportResponse;
import com.homefinmate.entity.LoanSimulation;
import com.homefinmate.entity.User;
import com.homefinmate.repository.LoanSimulationRepository;
import com.homefinmate.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

// ReportService 요약 리포트 검증 (최근 계산, DSR 추이 5건, 안내 문구, 0건)
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    LoanSimulationRepository loanSimulationRepository;
    @Mock
    UserRepository userRepository;
    @InjectMocks
    ReportService reportService;

    private LoanSimulation sim(long id, String dsr) {
        return LoanSimulation.builder().id(id).userId(7L)
                .loanAmount(new BigDecimal("300000000"))
                .monthlyPayment(new BigDecimal("1520055.93"))
                .dsr(dsr == null ? null : new BigDecimal(dsr))
                .build();
    }

    private void givenUserWith(List<LoanSimulation> sims) {
        when(userRepository.findByEmail("user@test.com"))
                .thenReturn(Optional.of(User.builder().id(7L).email("user@test.com").build()));
        when(loanSimulationRepository.findByUserIdOrderByCreatedAtDesc(7L)).thenReturn(sims);
    }

    // 7건: 총 개수, 최근 계산, 추이 5건, 적정 안내
    @Test
    @DisplayName("계산 기록 요약: 총 개수, 최근 계산, DSR 추이 5건")
    @SuppressWarnings("unchecked")
    void summary_withSimulations() {
        List<LoanSimulation> sims = LongStream.rangeClosed(1, 7).map(i -> 8 - i)
                .mapToObj(id -> sim(id, "0.3800")).toList(); // id 7,6,5,4,3,2,1 (최신순)
        givenUserWith(sims);

        ReportResponse res = reportService.generateSummary("user@test.com", "hfm-report-1");
        Map<String, Object> data = res.getReportData();

        assertThat(res.getReportType()).isEqualTo("SUMMARY");
        assertThat(res.getTraceId()).isEqualTo("hfm-report-1");
        assertThat(data.get("totalSimulations")).isEqualTo(7);
        assertThat(((Map<String, Object>) data.get("lastSimulation")).get("simulationId")).isEqualTo(7L);
        assertThat((List<Map<String, Object>>) data.get("dsrTrend"))
                .extracting(m -> m.get("simulationId")).containsExactly(7L, 6L, 5L, 4L, 3L);
        assertThat((String) data.get("eligibilityHint")).startsWith("Your DSR is within");
        assertThat(data.get("lastDsr")).isEqualTo(new BigDecimal("0.3800"));
    }

    // 최근 DSR 0.5067: HIGH, 경고 안내
    @Test
    @DisplayName("최근 DSR이 0.43을 넘으면 경고 안내")
    @SuppressWarnings("unchecked")
    void summary_highDsr_warns() {
        givenUserWith(List.of(sim(2, "0.5067"), sim(1, "0.2000")));

        Map<String, Object> data = reportService.generateSummary("user@test.com", null).getReportData();

        assertThat(((Map<String, Object>) data.get("lastSimulation")).get("dsrStatus")).isEqualTo("HIGH");
        assertThat((String) data.get("eligibilityHint")).startsWith("Your DSR is above");
    }

    // 기록 0건: 개수 0, 안내 메시지, 추이 없음
    @Test
    @DisplayName("기록이 없으면 0건과 안내 메시지")
    void summary_noSimulations() {
        givenUserWith(List.of());

        Map<String, Object> data = reportService.generateSummary("user@test.com", null).getReportData();

        assertThat(data.get("totalSimulations")).isEqualTo(0);
        assertThat(data).containsKey("message").doesNotContainKeys("lastSimulation", "dsrTrend");
    }

}
