package com.homefinmate.dto.risk;

import lombok.Builder;
import lombok.Data;

// 위험 평가 결과 (RiskWatch 결과 또는 fallback)
@Data
@Builder
public class RiskEvaluationResponse {
    // 위험 등급 (LOW, MEDIUM, HIGH, CRITICAL / fallback이면 UNKNOWN)
    private String riskLevel;
    // 위험 점수 (fallback이면 0)
    private Integer riskScore;
    // 설명 (RiskWatch의 reason)
    private String recommendation;
    private String traceId;
    // true: RiskWatch 실제 결과 / false: fallback (서비스 이용 불가)
    @Builder.Default
    private boolean available = true;
}
