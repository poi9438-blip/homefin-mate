package com.homefinmate.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

// 소득 AES-256-GCM 암호화/복호화
@Component
public class EncryptionUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int    IV_LENGTH = 12;
    private static final int    TAG_BITS  = 128;
    private static final int    KEY_BYTES = 32;

    // @Value는 값을 못 찾으면 서버가 안 켜짐
    @Value("${app.encryption.key}")
    private String encryptionKeyConfig;

    // 암호화: 매번 새 IV를 만들어 암호문 앞에 붙여서 Base64로 반환
    public String encrypt(String plainText) {
        try {
            byte[] iv = generateIv();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            
            cipher.init(Cipher.ENCRYPT_MODE, buildKey(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherBytes = cipher.doFinal(plainText.getBytes());
            byte[] combined = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherBytes, 0, combined, iv.length, cipherBytes.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    // 복호화: IV와 암호문 나눠서 복원
    public String decrypt(String encryptedBase64) {
        try {
            byte[] combined    = Base64.getDecoder().decode(encryptedBase64);
            byte[] iv          = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] cipherBytes = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, buildKey(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherBytes));
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
    
    private SecretKey buildKey() {
        byte[] raw    = encryptionKeyConfig.getBytes();
        byte[] keyBuf = new byte[KEY_BYTES];
        System.arraycopy(raw, 0, keyBuf, 0, Math.min(raw.length, KEY_BYTES));
        return new SecretKeySpec(keyBuf, "AES");
    }

    private byte[] generateIv() {
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }
}
