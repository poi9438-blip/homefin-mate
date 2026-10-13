package com.homefinmate.security;

import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// JwtUtil 토큰 발급/검증 특성 검증
class JwtUtilTest {

    private static final String SECRET = "HomeFin-Test-Secret-Key-Must-Be-At-Least-256-Bits-Long";

    private JwtUtil jwtUtil(String secret, long expirationMs) {
        JwtUtil util = new JwtUtil();
        ReflectionTestUtils.setField(util, "jwtSecret", secret);
        ReflectionTestUtils.setField(util, "jwtExpirationMs", expirationMs);
        return util;
    }

    private final JwtUtil jwtUtil = jwtUtil(SECRET, 3_600_000);

    @Test
    @DisplayName("발급한 토큰에서 이메일과 role 다시 꺼내기")
    void generate_then_extract() {
        String token = jwtUtil.generateToken("demo@test.com", "USER");

        assertThat(token.split("\\.")).hasSize(3); // 헤더.페이로드.서명
        assertThat(jwtUtil.validateToken(token)).isTrue();
        assertThat(jwtUtil.extractEmail(token)).isEqualTo("demo@test.com");
        assertThat(jwtUtil.extractRole(token)).isEqualTo("USER");
    }

    @Test
    @DisplayName("페이로드는 암호화가 아니라 Base64 - 키 없이도 읽을 수 있음")
    void payload_isReadableWithoutKey() {
        String token = jwtUtil.generateToken("demo@test.com", "USER");
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);

        System.out.println("페이로드 = " + payload);
        assertThat(payload).contains("\"sub\":\"demo@test.com\"").contains("\"roles\":[\"USER\"]");
    }

    @Test
    @DisplayName("페이로드를 ADMIN으로 고치면 서명이 맞지 않아 무효")
    void tamperedPayload_isInvalid() {
        String[] parts = jwtUtil.generateToken("demo@test.com", "USER").split("\\.");
        String adminPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                        .replace("\"USER\"", "\"ADMIN\"").getBytes(StandardCharsets.UTF_8));
        String tampered = parts[0] + "." + adminPayload + "." + parts[2];

        assertThat(jwtUtil.validateToken(tampered)).isFalse();
    }

    @Test
    @DisplayName("다른 비밀키로 만든 토큰은 무효")
    void otherSecret_isInvalid() {
        String token = jwtUtil("Another-Secret-Key-That-Is-Also-Long-Enough-123456", 3_600_000)
                .generateToken("demo@test.com", "ADMIN");

        assertThat(jwtUtil.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("만료된 토큰은 무효")
    void expiredToken_isInvalid() {
        String token = jwtUtil(SECRET, -1_000).generateToken("demo@test.com", "USER");

        assertThat(jwtUtil.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("32바이트보다 짧은 비밀키로는 토큰을 만들 수 없음")
    void shortSecret_isRejected() {
        assertThatThrownBy(() -> jwtUtil("too-short-secret", 3_600_000).generateToken("demo@test.com", "USER"))
                .isInstanceOf(WeakKeyException.class);
    }
}
