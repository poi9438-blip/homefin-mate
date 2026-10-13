package com.homefinmate.dto.loan;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

// DSR 단독 계산 결과 (DSR 비율, 판정)
@Data
@Builder
public class DsrResponse {
    // DSR (%)
    private BigDecimal dsr;
    // 판정 (ACCEPTABLE: 0.43 이하, HIGH: 초과)
    private String dsrStatus;
    private String traceId;
}
