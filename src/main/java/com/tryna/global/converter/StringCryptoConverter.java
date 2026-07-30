package com.tryna.global.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Converter
@Component
public class StringCryptoConverter implements AttributeConverter<String, String> {

    @Value("${app.crypto.secret-key}")
    private String secretKey;

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isBlank()) {
            return attribute;
        }
        try {
            byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

            // 1. 매번 새로운 랜덤 Nonce(IV) 생성
            byte[] iv = new byte[IV_LENGTH_BYTE];
            SecureRandom.getInstanceStrong().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);

            byte[] encrypted = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            // 2. IV(12바이트)와 암호문 결합: [IV + Ciphertext]
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new RuntimeException("토큰 암호화 중 오류가 발생했습니다.", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return dbData;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(dbData);
            byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

            // 3. 앞의 12바이트(IV)와 나머지(암호문) 분리
            byte[] iv = new byte[IV_LENGTH_BYTE];
            System.arraycopy(decoded, 0, iv, 0, IV_LENGTH_BYTE);

            int ciphertextLength = decoded.length - IV_LENGTH_BYTE;
            byte[] encrypted = new byte[ciphertextLength];
            System.arraycopy(decoded, IV_LENGTH_BYTE, encrypted, 0, ciphertextLength);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);

            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("토큰 복호화 중 오류가 발생했습니다.", e);
        }
    }
}