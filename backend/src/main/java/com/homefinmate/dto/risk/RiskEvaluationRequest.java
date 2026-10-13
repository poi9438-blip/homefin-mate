package com.homefinmate.dto.risk;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

// RiskWatch 위험 평가 요청 (RiskWatch 수신 형식에 맞춤)
@Data
@Builder
public class RiskEvaluationRequest {
    // 사용자 id (RiskWatch 형식에 맞춰 문자열)
    private String userId;
    private BigDecimal loanAmount;
    // 월 소득 (RiskWatch 필드명 monthlyIncome)
    private BigDecimal monthlyIncome;
    // DSR (%)
    private BigDecimal dsr;
    private String region;
    // 주택 보유 현황 (NONE, OWN, RENT)
    private String houseOwnership;
    // 보낸 시스템 이름
    private String sourceSystem;
    private String traceId;
    @Builder.Default
    private String eventVersion = "v1";
}
