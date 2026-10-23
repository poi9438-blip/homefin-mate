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

// 내 대출 계산 기록 요약 리포트 (총 횟수, 최근 계산, DTI 추이, 안내 문구)
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
            lastSim.put("dti", latest.getDti());
            lastSim.put("dtiStatus", dtiStatus(latest.getDti()));
            lastSim.put("dtiBasis", latest.getDtiBasis());
            lastSim.put("region", latest.getRegion());
            lastSim.put("createdAt", latest.getCreatedAt());
            data.put("lastSimulation", lastSim);
            // 최근 5건 DTI 추이 (최신순, 계산 기준도 함께)
            List<Map<String, Object>> trend = recent.stream()
                    .map(s -> {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("simulationId", s.getId());
                        entry.put("monthlyPayment", s.getMonthlyPayment());
                        entry.put("dti", s.getDti());
                        entry.put("dtiStatus", dtiStatus(s.getDti()));
                        entry.put("dtiBasis", s.getDtiBasis());
                        entry.put("createdAt", s.getCreatedAt());
                        return entry;
                    })
                    .toList();
            data.put("dtiTrend", trend);

            // 최근 DTI 기준 안내 문구
            if (latest.getDti() != null) {
                String hint = "ACCEPTABLE".equals(DtiRule.status(latest.getDti()))
                        ? "Your DTI is within the acceptable range. You may qualify for standard loan products."
                        : "Your DTI is above the recommended threshold. Consider reviewing your debt structure.";
                data.put("eligibilityHint", hint);
            }

            // 대시보드 요약 카드용 최근 DTI
            data.put("lastDti", latest.getDti());
        } else {
            data.put("message", "No simulations found. Run a loan calculation to generate data.");
        }

        log.info("[{}] Report summary generated for userId={} simCount={}",
                traceId, userId, total);

        return buildResponse(userId, email, data, traceId);
    }

    // DTI 판정 (값 없으면 UNKNOWN)
    private String dtiStatus(BigDecimal dti) {
        if (dti == null)
            return "UNKNOWN";
        return DtiRule.status(dti);
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
