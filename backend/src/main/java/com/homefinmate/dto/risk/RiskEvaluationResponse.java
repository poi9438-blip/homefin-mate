package com.homefinmate.dto.risk;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

// 위험 평가 결과 (RiskWatch 판정, fallback, 또는 판정 안 함)
@Data
@Builder
public class RiskEvaluationResponse {

    // EVALUATED: RiskWatch 판정 / UNAVAILABLE: 호출 실패 fallback / NOT_EVALUATED: 추천 상품
    // 없음·입력 부족으로 부르지 않음
    public enum Status {
        EVALUATED, UNAVAILABLE, NOT_EVALUATED
    }

    // 규칙 하나의 근거 (RiskWatch가 보낸 값)
    public record Reason(String code, String name, BigDecimal threshold, BigDecimal actual, String result,
            String detail) {
    }

    @Builder.Default
    private Status status = Status.EVALUATED;
    // 위험 등급 (LOW, MEDIUM, HIGH / 판정하지 않았으면 UNKNOWN)
    private String riskLevel;
    // 위험 점수 (판정하지 않았으면 0)
    private Integer riskScore;
    // 설명 (판정함: RiskWatch 한 문장, 판정 안 함: 이유)
    private String recommendation;
    private String traceId;
    // true: RiskWatch 실제 결과 / false: fallback 또는 판정 안 함
    @Builder.Default
    private boolean available = true;

    // 아래는 판정했을 때만 (RiskWatch 4단계 평가 API)
    private String evaluationId;
    private String productCode;
    // ELIGIBLE / REVIEW / INELIGIBLE
    private String verdict;
    private String criteriaVersion;
    private List<Reason> reasons;
    // 입력이 없어 RiskWatch도 판정하지 않는 기준 (신용점수 등)
    private List<String> notEvaluated;

    // RiskWatch를 부르지 않음 (추천 상품 없음·입력 부족, 이유는 화면에 그대로 보여 줄 한국어 문장)
    public static RiskEvaluationResponse notEvaluated(String reason, String traceId) {
        return RiskEvaluationResponse.builder()
                .status(Status.NOT_EVALUATED)
                .riskLevel("UNKNOWN")
                .riskScore(0)
                .recommendation(reason)
                .traceId(traceId)
                .available(false)
                .build();
    }
}
