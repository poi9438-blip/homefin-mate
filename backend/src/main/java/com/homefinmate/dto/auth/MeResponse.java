package com.homefinmate.dto.auth;

// 현재 로그인 사용자 (이메일, 역할)
public record MeResponse(String email, String role) {
}
