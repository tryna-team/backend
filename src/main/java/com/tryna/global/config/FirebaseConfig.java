package com.tryna.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.List;

// Firebase Admin SDK 초기화.
@Slf4j
@Component
public class FirebaseConfig {

    private static final String FCM_MESSAGING_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    @Value("${firebase.credentials-base64:}")
    private String credentialsBase64;

    @PostConstruct
    public void initialize() {
        if (!StringUtils.hasText(credentialsBase64)) {
            log.warn("FIREBASE_CREDENTIALS_BASE64가 설정되지 않아 FCM 푸시 기능이 비활성화됩니다.");
            return;
        }

        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }

        try {
            byte[] decodedKey = Base64.getDecoder().decode(credentialsBase64);
            GoogleCredentials credentials = GoogleCredentials
                    .fromStream(new ByteArrayInputStream(decodedKey))
                    .createScoped(List.of(FCM_MESSAGING_SCOPE));

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build();

            FirebaseApp.initializeApp(options);
            log.info("Firebase Admin SDK 초기화에 성공했습니다.");
        } catch (Exception e) {
            log.error("Firebase Admin SDK 초기화에 실패했습니다.", e);
        }
    }
}
