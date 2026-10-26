package com.homefinmate.dto.auth;

import com.homefinmate.common.TraceIdCarrier;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

// 회원가입 요청 (이메일, 비밀번호)
@Data
public class SignupRequest implements TraceIdCarrier {
    // 필수, 이메일 형식
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    // 필수, 8자 이상
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    // 요청 traceId (보낸 값과 관계없이 서버가 X-Trace-Id 기준 값으로 덮어씀, #90)
    private String traceId;
    private String eventVersion = "v1";
}
