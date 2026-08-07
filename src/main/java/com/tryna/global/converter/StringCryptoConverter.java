package com.tryna.global.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Converter
@Component
public class StringCryptoConverter implements AttributeConverter<String, String> {

    private static String secretKey;

    // 생성자가 아닌 메서드 주입을 통해 static 변수에 값을 세팅
    @Value("${app.crypto.secret-key}")
    public void setSecretKey(String value) {
        secretKey = value;
    }

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;

    // 1. Fail-fast 검증 메서드 분리
    private void validateSecretKey() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("app.crypto.secret-key 미설정 (JPA Converter 초기화 오류)");
        }
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isBlank()) {
            return attribute;
        }

        validateSecretKey(); // NPE 방지 (Fail-fast)

        byte[] keyBytes = null;
        try {
            // 2. SHA-256을 사용해 어떤 문자열이든 32바이트(256비트) 길이의 안전한 키로 변환
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            keyBytes = sha256.digest(secretKey.getBytes(StandardCharsets.UTF_8));
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

            // 3. getInstanceStrong() 대신 new SecureRandom() 사용 (블로킹 이슈 방지)
            byte[] iv = new byte[IV_LENGTH_BYTE];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);

            byte[] encrypted = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            // IV(12바이트)와 암호문 결합: [IV + Ciphertext]
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new RuntimeException("토큰 암호화 중 오류가 발생했습니다.", e);
        }finally {
            // 4. 메모리에 남은 민감한 키 데이터 배열을 명시적으로 0화(Zeroing) 처리
            if (keyBytes != null) {
                Arrays.fill(keyBytes, (byte) 0);
            }
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return dbData;
        }

        validateSecretKey(); // NPE 방지 (Fail-fast)

        byte[] keyBytes = null;
        try {
            byte[] decoded = Base64.getDecoder().decode(dbData);

            // 2. SHA-256을 사용해 32바이트(256비트) 길이의 안전한 키로 변환
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            keyBytes = sha256.digest(secretKey.getBytes(StandardCharsets.UTF_8));
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

            // 앞의 12바이트(IV)와 나머지(암호문) 분리
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
        } finally {
            // 4. 메모리에 남은 민감한 키 데이터 배열을 명시적으로 0화(Zeroing) 처리
            if (keyBytes != null) {
                Arrays.fill(keyBytes, (byte) 0);
            }
        }
    }
}