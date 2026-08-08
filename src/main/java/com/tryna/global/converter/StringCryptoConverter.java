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
    private static boolean isInitialized = false;

    // 의존성 주입 시점(서버 기동 시)에 바로 값 검증 (Fail-fast)
    @Value("${app.crypto.secret-key}")
    public void setSecretKey(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("app.crypto.secret-key가 비어있습니다. 서버 기동을 중단합니다.");
        }
        secretKey = value;
        isInitialized = true; // 주입 및 검증 완료 마킹
    }

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;

    // 1. 런타임 방어 메서드 (혹시 모를 라이프사이클 꼬임 방지)
    private void validateSecretKey() {
        if (!isInitialized || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("StringCryptoConverter가 아직 초기화되지 않았거나 secret-key가 누락되었습니다.");
        }
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isBlank()) {
            return attribute;
        }

        validateSecretKey();

        try {
            // 쓰기(저장) 시에는 무조건 최신 V2 방식(SHA-256)을 사용합니다.
            byte[] payload = encryptPayload(attribute, true);
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

        validateSecretKey();

        try {
            byte[] decoded = Base64.getDecoder().decode(dbData);

            try {
                // 1. 최신 방식 (SHA-256 파생 키)으로 복호화 시도
                return decryptPayload(decoded, true);
            } catch (Exception e) {
                // 2. 복호화 실패 시(Tag Mismatch 등), 레거시 방식(Direct 바이트 키)으로 폴백 시도
                // 기존 V1 데이터 조회를 안전하게 지원하기 위한 읽기 전용 폴백입니다.
                return decryptPayload(decoded, false);
            }
        } catch (Exception e) {
            throw new RuntimeException("토큰 복호화 중 오류가 발생했습니다.", e);
        }
    }

    // --- [내부 핵심 암/복호화 및 키 생성 모듈] ---

    private byte[] encryptPayload(String attribute, boolean useSha256) throws Exception {
        byte[] keyBytes = null;
        try {
            keyBytes = deriveKey(useSha256);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

            byte[] iv = new byte[IV_LENGTH_BYTE];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);

            byte[] encrypted = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);

            return payload;
        } finally {
            if (keyBytes != null) {
                Arrays.fill(keyBytes, (byte) 0);
            }
        }
    }

    private String decryptPayload(byte[] decoded, boolean useSha256) throws Exception {
        byte[] keyBytes = null;
        try {
            keyBytes = deriveKey(useSha256);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

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
        } finally {
            if (keyBytes != null) {
                Arrays.fill(keyBytes, (byte) 0);
            }
        }
    }

    /**
     * @param useSha256 true면 최신 SHA-256 해시 키(V2), false면 레거시 문자열 바이트(V1) 반환
     */
    private byte[] deriveKey(boolean useSha256) throws Exception {
        if (useSha256) {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return sha256.digest(secretKey.getBytes(StandardCharsets.UTF_8));
        } else {
            // 레거시: 과거에 사용했던 Direct Secret-key bytes 반환
            return secretKey.getBytes(StandardCharsets.UTF_8);
        }
    }
}