package com.homefinmate.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Base64;

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
    @DisplayName("[TC-ENC-001] 암호화한 값을 복호화하면 원래 값으로")
    void encrypt_then_decrypt() {
        String encrypted = util.encrypt("2666667");

        System.out.println("암호문 = " + encrypted);
        assertThat(encrypted).isNotEqualTo("2666667");
        assertThat(util.decrypt(encrypted)).isEqualTo("2666667");
    }

    @Test
    @DisplayName("[TC-ENC-002] 같은 값을 암호화해도 결과가 다름")
    void sameValue_differentCiphertext() {
        assertThat(util.encrypt("2666667")).isNotEqualTo(util.encrypt("2666667"));
    }

    @Test
    @DisplayName("[TC-ENC-003] 다른 키로는 복호화 실패")
    void wrongKey_cannotDecrypt() {
        String encrypted = util.encrypt("2666667");
        EncryptionUtil other = utilWithKey("Another-Key-For-Test-32-Bytes-!!");

        assertThatThrownBy(() -> other.decrypt(encrypted))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Decryption failed");
    }

    // 변조된 암호문은 복호화 실패 (GCM 무결성)
    @Test
    @DisplayName("[TC-ENC-201] 암호문을 1바이트 바꾸면 복호화 실패")
    void tamperedCiphertext_cannotDecrypt() {
        byte[] bytes = Base64.getDecoder().decode(util.encrypt("2666667"));
        bytes[bytes.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(bytes);

        assertThatThrownBy(() -> util.decrypt(tampered))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Decryption failed");
    }

    // 암호문 형식이 아니면 복호화 실패 (같은 메시지)
    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = { "not-base64!!", "AAAA", "" })
    @DisplayName("[TC-ENC-202] Base64가 아니거나 IV보다 짧은 값은 복호화 실패")
    void malformedInput_cannotDecrypt(String input) {
        assertThatThrownBy(() -> util.decrypt(input))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Decryption failed");
    }

    // 저장 형식(IV 12 + 암호문 + 태그 16)과 IV가 매번 다른지
    @Test
    @DisplayName("[TC-ENC-203] 암호문 길이는 IV 12 + 평문 + 태그 16바이트, IV는 매번 다름")
    void ciphertextLayout_andFreshIv() {
        byte[] first = Base64.getDecoder().decode(util.encrypt("2666667"));
        byte[] second = Base64.getDecoder().decode(util.encrypt("2666667"));

        assertThat(first).hasSize(12 + "2666667".length() + 16);
        assertThat(Arrays.copyOfRange(first, 0, 12)).isNotEqualTo(Arrays.copyOfRange(second, 0, 12));
    }
}
