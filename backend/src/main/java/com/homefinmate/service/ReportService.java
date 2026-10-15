package com.homefinmate.service;

import com.homefinmate.dto.report.ReportResponse;
import com.homefinmate.entity.LoanSimulation;
import com.homefinmate.repository.LoanSimulationRepository;
import com.homefinmate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 내 대출 계산 기록 요약 리포트 (총 횟수, 최근 계산, DSR 추이, 안내 문구)
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final LoanSimulationRepository loanSimulationRepository;
    private final UserRepository userRepository;

    // 요약 리포트 만들기
    public ReportResponse generateSummary(String email, String traceId) {
        Long userId = userRepository.findByEmail(email).map(u -> u.getId()).orElse(null);

        Map<String, Object> data = new LinkedHashMap<>();

        // 사용자 없음: 0건 리포트
        if (userId == null) {
            data.put("totalSimulations", 0);
            data.put("message", "No simulation data found for this account.");
            return buildResponse(1L, email, data, traceId);
        }

        // 계산 수(COUNT)와 최근 5건(최신순)만 조회
        long total = loanSimulationRepository.countByUserId(userId);
        List<LoanSimulation> recent = loanSimulationRepository.findTop5ByUserIdOrderByCreatedAtDesc(userId);

        data.put("totalSimulations", Math.toIntExact(total));

        if (!recent.isEmpty()) {
            LoanSimulation latest = recent.get(0);

            // 가장 최근 계산
            Map<String, Object> lastSim = new LinkedHashMap<>();
            lastSim.put("simulationId", latest.getId());
            lastSim.put("loanAmount", latest.getLoanAmount());
            lastSim.put("monthlyPayment", latest.getMonthlyPayment());
            lastSim.put("totalInterest", latest.getTotalInterest());
            lastSim.put("dsr", latest.getDsr());
            lastSim.put("dsrStatus", dsrStatus(latest.getDsr()));
            lastSim.put("region", latest.getRegion());
            lastSim.put("createdAt", latest.getCreatedAt());
            data.put("lastSimulation", lastSim);
            // 최근 5건 DSR 추이 (최신순)
            List<Map<String, Object>> trend = recent.stream()
                    .map(s -> {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("simulationId", s.getId());
                        entry.put("monthlyPayment", s.getMonthlyPayment());
                        entry.put("dsr", s.getDsr());
                        entry.put("dsrStatus", dsrStatus(s.getDsr()));
                        entry.put("createdAt", s.getCreatedAt());
                        return entry;
                    })
                    .toList();
            data.put("dsrTrend", trend);

            // 최근 DSR 기준 안내 문구
            if (latest.getDsr() != null) {
                String hint = latest.getDsr().compareTo(BigDecimal.valueOf(0.43)) <= 0
                        ? "Your DSR is within the acceptable range. You may qualify for standard loan products."
                        : "Your DSR is above the recommended threshold. Consider reviewing your debt structure.";
                data.put("eligibilityHint", hint);
            }

            // 대시보드 요약 카드용 최근 DSR
            data.put("lastDsr", latest.getDsr());
        } else {
            data.put("message", "No simulations found. Run a loan calculation to generate data.");
        }

        log.info("[{}] Report summary generated for userId={} simCount={}",
                traceId, userId, total);

        return buildResponse(userId, email, data, traceId);
    }

    // DSR 판정 (값 없으면 UNKNOWN)
    private String dsrStatus(BigDecimal dsr) {
        if (dsr == null)
            return "UNKNOWN";
        return dsr.compareTo(BigDecimal.valueOf(0.43)) <= 0 ? "ACCEPTABLE" : "HIGH";
    }

    // 응답 만들기 (종류 SUMMARY, 생성 시각 지금)
    private ReportResponse buildResponse(Long reportId, String email, Map<String, Object> data, String traceId) {
        return ReportResponse.builder()
                .reportId(reportId)
                .reportType("SUMMARY")
                .reportData(data)
                .generatedAt(LocalDateTime.now())
                .traceId(traceId)
                .build();
    }
}
