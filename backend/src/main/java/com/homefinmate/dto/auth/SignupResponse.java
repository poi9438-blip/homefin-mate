package com.homefinmate.dto.auth;

import lombok.Builder;
import lombok.Data;

// 회원가입 응답
@Data @Builder
public class SignupResponse {
    private Long userId;
    private String email;
    private String role;
    private String traceId;
}
