package com.homefinmate.security;

// 로그인한 사용자 (JwtAuthFilter가 DB에서 확인한 id와 이메일)
public record AuthUser(Long id, String email) {
}
