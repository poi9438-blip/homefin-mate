package com.homefinmate.dto.auth;

import lombok.Builder;
import lombok.Data;

// 로그인 응답 (JWT, 이메일, 역할)
@Data
@Builder
public class LoginResponse {
    private String token;
    private String email;
    private String role;
    private String traceId;
}
