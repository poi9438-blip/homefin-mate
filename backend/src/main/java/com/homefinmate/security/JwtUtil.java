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

    // 유효한 토큰이면 이메일, 아니면 null (서명·만료·형식을 한 번에 확인)
    public String validEmail(String token) {
        try {
            return parseClaims(token).getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    // 서명을 확인하고 내용(claims) 꺼내기 (실패하면 예외)
    // 토큰의 만료 시각 (epoch 밀리초)
    public long expiresAtMillis(String token) {
        return parseClaims(token).getExpiration().getTime();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
