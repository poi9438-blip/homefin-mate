package com.homefinmate.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;

// 로그인 응답 (이메일, 역할, 로그인 만료 시각). 토큰은 HttpOnly 쿠키로만 보냄 (#78)
@Data
@Builder
public class LoginResponse {
    @JsonIgnore
    private String token;
    // 로그인 만료 시각 (epoch 밀리초, 토큰의 exp)
    private Long expiresAt;
    private String email;
    private String role;
    private String traceId;
}
