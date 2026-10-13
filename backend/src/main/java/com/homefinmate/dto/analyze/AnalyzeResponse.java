package com.homefinmate.dto.analyze;

import com.homefinmate.dto.analysis.RegionAnalysisResponse;
import com.homefinmate.dto.loan.LoanCalculationResponse;
import com.homefinmate.dto.policy.PolicyRecommendationResponse;
import com.homefinmate.dto.risk.RiskEvaluationResponse;
import lombok.Builder;
import lombok.Data;

// 통합 분석 결과 (대출, 정책, 위험 평가, 지역 분석)
@Data
@Builder
public class AnalyzeResponse {
    private LoanCalculationResponse loan;
    private PolicyRecommendationResponse policy;
    private RiskEvaluationResponse risk;
    private RegionAnalysisResponse region;
    private String traceId;
}
