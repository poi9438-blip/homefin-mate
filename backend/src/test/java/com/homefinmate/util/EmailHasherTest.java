package com.homefinmate.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 이메일 HMAC: 같은 이메일은 같은 값, 이메일이 드러나지 않음, 키가 다르면 다른 값
class EmailHasherTest {

    private final EmailHasher hasher = new EmailHasher("HomeFin-AES-256-Key-Test-Padded!");

    @Test
    @DisplayName("[TC-ENC-004] 이메일 HMAC: 같은 이메일(대소문자·공백 무시)은 같은 값, 이메일 미포함, 키가 다르면 다른 값")
    void hash_isDeterministicKeyedAndHidesEmail() {
        String hash = hasher.hash("User@Test.com");

        assertThat(hash).startsWith("hmac:").hasSize("hmac:".length() + 64).doesNotContain("user")
                .doesNotContain("test");
        assertThat(hasher.hash(" user@test.com ")).isEqualTo(hash);
        assertThat(hasher.hash("other@test.com")).isNotEqualTo(hash);
        assertThat(new EmailHasher("Another-Key-For-Hmac-Test-32bytes").hash("user@test.com")).isNotEqualTo(hash);
    }
}
