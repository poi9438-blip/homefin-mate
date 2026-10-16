package com.homefinmate.dto.policy;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// MET(충족) / NOT_MET(미충족) / UNKNOWN(입력이 없거나 가구 조건에 따라 달라서 확인 필요)
// 조건 하나의 판정 (조건 이름, 결과, 근거 설명)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PolicyCondition {

    public enum Result {
        MET, NOT_MET, UNKNOWN
    }

    private String name;
    private Result result;
    private String detail;
}
