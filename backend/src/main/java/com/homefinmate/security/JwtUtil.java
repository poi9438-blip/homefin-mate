package com.homefinmate.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

// JWT 발급과 검증/정보 추출
@Component
public class JwtUtil {

    // 서명용 비밀키
    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    // 문자열 비밀키 => HMAC-SHA 서명용 키 객체
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    // 토큰 발급: 이메일, 역할, 발급/만료 시각을 담아 서명
    public String generateToken(String email, String role) {
        return Jwts.builder()
                .subject(email)
                .claim("roles", java.util.List.of(role))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    // 토큰에서 이메일 꺼내기
    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    // 토큰에서 role 꺼내기
    public String extractRole(String token) {
        Object roles = parseClaims(token).get("roles");
        if (roles instanceof java.util.List<?> list && !list.isEmpty()) {
            return String.valueOf(list.get(0));
        }
        return "";
    }

    // 토큰 유효성 검사 (서명, 만료, 형식)
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // TODO: 요청 한 번에 같은 토큰을 3번 검증하는 부분 수정할 것
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
