package com.homefinmate.dto.loan;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

//  DTI 단독 계산 결과 (DTI 비율, 판정)
@Data
@Builder
public class DtiResponse {
    // DTI (0~1, 예: 0.38 = 38%)
    private BigDecimal dti;
    // 판정 (ACCEPTABLE: 0.60 이하, HIGH: 초과)
    private String dtiStatus;
    private String traceId;
}
