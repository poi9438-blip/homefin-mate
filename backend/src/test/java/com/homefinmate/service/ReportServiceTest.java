package com.homefinmate.service;

import com.homefinmate.dto.report.ReportResponse;
import com.homefinmate.entity.LoanSimulation;
import com.homefinmate.repository.LoanSimulationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// ReportService 요약 리포트 검증 (최근 계산, DTI 추이 5건, 안내 문구, 0건)
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    LoanSimulationRepository loanSimulationRepository;
    @InjectMocks
    ReportService reportService;

    private LoanSimulation sim(long id, String dti) {
        return LoanSimulation.builder().id(id).userId(7L)
                .loanAmount(new BigDecimal("300000000"))
                .monthlyPayment(new BigDecimal("1520055.93"))
                .dti(dti == null ? null : new BigDecimal(dti))
                .dtiBasis(DtiRule.WITH_EXISTING_INTEREST)
                .build();
    }

    private void givenUserWith(List<LoanSimulation> sims) {
        when(loanSimulationRepository.countByUserId(7L)).thenReturn((long) sims.size());
        when(loanSimulationRepository.findTop5ByUserIdOrderByCreatedAtDesc(7L))
                .thenReturn(sims.stream().limit(5).toList());
    }

    // 7건: 총 개수, 최근 계산, 추이 5건, 적정 안내
    @Test
    @DisplayName("[TC-RPT-001] 계산 기록 요약: 총 개수, 최근 계산, DTI 추이 5건")
    @SuppressWarnings("unchecked")
    void summary_withSimulations() {
        List<LoanSimulation> sims = LongStream.rangeClosed(1, 7).map(i -> 8 - i)
                .mapToObj(id -> sim(id, "0.3800")).toList(); // id 7,6,5,4,3,2,1 (최신순)
        givenUserWith(sims);

        ReportResponse res = reportService.generateSummary(7L, "hfm-report-1");
        Map<String, Object> data = res.getReportData();

        assertThat(res.getReportType()).isEqualTo("SUMMARY");
        assertThat(res.getTraceId()).isEqualTo("hfm-report-1");
        assertThat(data.get("totalSimulations")).isEqualTo(7);
        assertThat(((Map<String, Object>) data.get("lastSimulation")).get("simulationId")).isEqualTo(7L);
        assertThat((List<Map<String, Object>>) data.get("dtiTrend"))
                .extracting(m -> m.get("simulationId")).containsExactly(7L, 6L, 5L, 4L, 3L);
        assertThat((String) data.get("eligibilityHint")).startsWith("Your DTI is within");
        assertThat(data.get("lastDti")).isEqualTo(new BigDecimal("0.3800"));
    }

    // 최근 DTI 0.6080: HIGH, 경고 안내
    @Test
    @DisplayName("[TC-RPT-002] 최근 DTI가 0.60을 넘으면 경고 안내")
    @SuppressWarnings("unchecked")
    void summary_highDsr_warns() {
        givenUserWith(List.of(sim(2, "0.6080"), sim(1, "0.2000")));

        Map<String, Object> data = reportService.generateSummary(7L, null).getReportData();

        assertThat(((Map<String, Object>) data.get("lastSimulation")).get("dtiStatus")).isEqualTo("HIGH");
        assertThat((String) data.get("eligibilityHint")).startsWith("Your DTI is above");
    }

    // 기록 0건: 개수 0, 안내 메시지, 추이 없음
    @Test
    @DisplayName("[TC-RPT-003] 기록이 없으면 0건과 안내 메시지")
    void summary_noSimulations() {
        givenUserWith(List.of());

        Map<String, Object> data = reportService.generateSummary(7L, null).getReportData();

        assertThat(data.get("totalSimulations")).isEqualTo(0);
        assertThat(data).containsKey("message").doesNotContainKeys("lastSimulation", "dtiTrend");
    }

    // 계산이 많아도 전체 조회 없이 COUNT + 최근 5건만 사용
    @Test
    @DisplayName("[TC-RPT-302] 계산 10만 건이어도 전체 목록을 조회하지 않고 COUNT·최근 5건만 사용")
    void summary_largeHistory_usesCountAndTop5Only() {
        when(loanSimulationRepository.countByUserId(7L)).thenReturn(100_000L);
        when(loanSimulationRepository.findTop5ByUserIdOrderByCreatedAtDesc(7L))
                .thenReturn(List.of(sim(5, "0.3800"), sim(4, "0.3800"), sim(3, "0.3800"), sim(2, "0.3800"),
                        sim(1, "0.3800")));

        Map<String, Object> data = reportService.generateSummary(7L, null).getReportData();

        assertThat(data.get("totalSimulations")).isEqualTo(100_000);
        verify(loanSimulationRepository, never()).findByUserIdOrderByCreatedAtDesc(any());
    }

}
