package com.homefinmate.dto.analysis;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// 지역 분석 결과 (지역, 예시 평균 가격·성장률·위험 점수, 예시 여부, 이용 가능 여부)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegionAnalysisResponse {
    private String region;
    // 평균 주택 가격 (예시 데이터, 출처가 없어 단위 모름)
    @JsonAlias("average_price")
    private BigDecimal averagePrice;
    // 연 성장률 (0~1)
    @JsonAlias("growth_rate")
    private BigDecimal growthRate;
    // 시장 위험 점수 (예시 데이터, 기준 없음)
    @JsonAlias("risk_score")
    private BigDecimal riskScore;
    // 예시 데이터 여부 (true: 실제 시세 아님, 출처 없음)
    private Boolean sample;
    // 이용 가능 여부 (false면 값 없음, unavailableReason에 이유)
    @Builder.Default
    private boolean available = true;
    // 이용 불가 이유 (UNSUPPORTED_REGION: 데이터 없는 지역, SERVICE_UNAVAILABLE: Python 서비스 연결
    // 실패)
    private String unavailableReason;
    @JsonAlias("trace_id")
    private String traceId;
}
