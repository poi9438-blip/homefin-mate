package com.homefinmate.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// EncryptionUtil 암호화/복호화 특성 검증
class EncryptionUtilTest {
    private EncryptionUtil utilWithKey(String key) {
        EncryptionUtil util = new EncryptionUtil();
        ReflectionTestUtils.setField(util, "encryptionKeyConfig", key);
        return util;
    }

    private final EncryptionUtil util = utilWithKey("HomeFin-AES-256-Key-Test-Padded!");

    @Test
    @DisplayName("암호화한 값을 복호화하면 원래 값으로")
    void encrypt_then_decrypt() {
        String encrypted = util.encrypt("2666667");

        System.out.println("암호문 = " + encrypted);
        assertThat(encrypted).isNotEqualTo("2666667");
        assertThat(util.decrypt(encrypted)).isEqualTo("2666667");
    }

    @Test
    @DisplayName("같은 값 암호화해도 결과가 다름")
    void sameValue_differentCiphertext() {
        assertThat(util.encrypt("2666667")).isNotEqualTo(util.encrypt("2666667"));
    }

    @Test
    @DisplayName("다른 키로 복호화")
    void wrongKey_cannotDecrypt() {
        String encrypted = util.encrypt("2666667");
        EncryptionUtil other = utilWithKey("Another-Key-For-Test-32-Bytes-!!");

        assertThatThrownBy(() -> other.decrypt(encrypted))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Decryption failed");
    }
}
