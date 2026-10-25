package com.homefinmate.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

// 숫자를 AES-GCM 암호문으로 저장하고 읽을 때 복호화 (DTI로 소득을 역산하지 못하게, #31)
@Component
@Converter
@RequiredArgsConstructor
public class EncryptedDecimalConverter implements AttributeConverter<BigDecimal, String> {

    private final EncryptionUtil encryptionUtil;

    @Override
    public String convertToDatabaseColumn(BigDecimal value) {
        return value == null ? null : encryptionUtil.encrypt(value.toPlainString());
    }

    @Override
    public BigDecimal convertToEntityAttribute(String encrypted) {
        return encrypted == null ? null : new BigDecimal(encryptionUtil.decrypt(encrypted));
    }
}
