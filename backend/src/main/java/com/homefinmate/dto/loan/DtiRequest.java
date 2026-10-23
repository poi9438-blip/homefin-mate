package com.homefinmate.dto.loan;

import lombok.Data;

import java.math.BigDecimal;

// DTI 단독 계산 요청 (신규 대출 월 상환액, 기존 대출 월 이자, 월 소득)
@Data
public class DtiRequest {
    // 신규 대출 월 상환액 (원)
    private BigDecimal monthlyPayment;

    // 기존 대출 월 이자 (원, 없으면 0)
    private BigDecimal existingMonthlyInterest;

    // 월 소득 (원)
    private BigDecimal monthlyIncome;

    private String traceId;
    private String eventVersion = "v1";
}
