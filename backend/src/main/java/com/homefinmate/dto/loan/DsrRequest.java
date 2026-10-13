package com.homefinmate.dto.loan;

import lombok.Data;

import java.math.BigDecimal;

// DSR 단독 계산 요청 (월 부채 상환액, 세전 월 소득)
@Data
public class DsrRequest {
    // 월 부채 상환액 (원)
    private BigDecimal monthlyDebt;

    // 세전 월 소득 (원)
    private BigDecimal grossMonthlyIncome;

    private String traceId;
    private String eventVersion = "v1";
}
