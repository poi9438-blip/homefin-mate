package com.homefinmate.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

// BCrypt 비밀번호 해시 특성 검증
class PasswordEncoderTest {

    private final PasswordEncoder encoder = new SecurityConfig().passwordEncoder();

    @Test
    @DisplayName("같은 비밀번호라도 해시할 때마다 결과가 다름 (salt)")
    void sameRawPassword_differentHash() {
        String hash1 = encoder.encode("demo1234");
        String hash2 = encoder.encode("demo1234");

        System.out.println("hash1 = " + hash1);
        System.out.println("hash2 = " + hash2);

        assertThat(hash1).isNotEqualTo(hash2);
        assertThat(hash1).startsWith("$2a$10$").hasSize(60);
    }

    @Test
    @DisplayName("matches()로 비교")
    void matches() {
        String hash = encoder.encode("demo1234");

        assertThat(encoder.matches("demo1234", hash)).isTrue();
        assertThat(encoder.matches("wrong-pw", hash)).isFalse();
    }
}
