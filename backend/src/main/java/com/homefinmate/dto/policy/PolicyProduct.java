package com.homefinmate.dto.policy;

import lombok.Builder;
import lombok.Data;

import java.util.List;

// 점수가 매겨진 정책 상품 (이름, 점수, 설명, 추천 이유)
@Data
@Builder
public class PolicyProduct {
    private String name;
    private int score;
    private String description;
    private List<String> reasons;
}
