package com.homefinmate.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// 관리자 로그인 실패 검색 요청 (찾을 이메일, 서버에서 HMAC으로 바꿔 검색)
@Data
public class LoginFailureSearchRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    // 앞뒤 공백 제거 후 저장 (검증은 정리된 값으로)
    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }
}
