package com.homefinmate.dto.auth;

import com.homefinmate.common.TraceIdCarrier;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// 로그인 요청 (이메일, 비밀번호)
@Data
public class LoginRequest implements TraceIdCarrier {
    // 필수, 이메일 형식
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    // 필수 (길이 검사는 가입 시에만)
    @NotBlank(message = "Password is required")
    private String password;

    // 요청 traceId (보낸 값과 관계없이 서버가 X-Trace-Id 기준 값으로 덮어씀, #90)
    private String traceId;
    private String eventVersion = "v1";
}
