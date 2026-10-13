package com.homefinmate.dto.analysis;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

// 지역 분석 결과 (지역, 평균 가격, 성장률, 위험 점수)
@Data
@Builder
public class RegionAnalysisResponse {
    private String region;
    // 평균 주택 가격 (예시 데이터, 단위 불명확)
    private BigDecimal averagePrice;
    // 연 성장률 (%)
    private BigDecimal growthRate;
    // 시장 위험 점수
    private BigDecimal riskScore;
    private String traceId;
}
