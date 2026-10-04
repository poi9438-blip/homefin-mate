package com.homefinmate.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

// 회원가입 요청 (이메일, 비밀번호, 선택 입력 소득)
@Data
public class SignupRequest {   
    // 필수, 이메일 형식
    @NotBlank @Email
    private String email;

    // 필수, 8자 이상
    @NotBlank @Size(min = 8)
    private String password;

    // 월 소득 (선택, 암호화해서 저장)
    private String income;

    private String traceId;
    private String eventVersion = "v1";
}
