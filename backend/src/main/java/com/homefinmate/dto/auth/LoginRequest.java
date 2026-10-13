package com.homefinmate.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// 로그인 요청 (이메일, 비밀번호)
@Data
public class LoginRequest {
    // 필수, 이메일 형식
    @NotBlank
    @Email
    private String email;

    // 필수 (길이 검사는 가입 시에만)
    @NotBlank
    private String password;

    private String traceId;
    private String eventVersion = "v1";
}
