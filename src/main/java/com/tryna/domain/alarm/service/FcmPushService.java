package com.tryna.domain.alarm.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Slf4j
@Service
public class FcmPushService {

    public enum TokenValidationResult {
        VALID,
        INVALID,
        UNAVAILABLE
    }

    // F100 push-token: 토큰이 실제 FCM에 등록되어 있는지 dry-run 발송으로 검증
    public TokenValidationResult validateToken(String fcmToken) {
        if (!StringUtils.hasText(fcmToken)) {
            return TokenValidationResult.INVALID;
        }

        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("Firebase가 초기화되지 않아 FCM 토큰 형식만 검증합니다.");
            return TokenValidationResult.VALID;
        }

        Message message = Message.builder()
                .setToken(fcmToken)
                .build();

        try {
            FirebaseMessaging.getInstance().send(message, true); // dry run: 실제 발송 없이 유효성만 검증
            return TokenValidationResult.VALID;
        } catch (FirebaseMessagingException e) {
            MessagingErrorCode errorCode = e.getMessagingErrorCode();
            if (errorCode == MessagingErrorCode.INVALID_ARGUMENT
                    || errorCode == MessagingErrorCode.UNREGISTERED
                    || errorCode == MessagingErrorCode.SENDER_ID_MISMATCH) {
                log.warn("유효하지 않은 FCM 토큰입니다. errorCode={}", errorCode);
                return TokenValidationResult.INVALID;
            }

            log.error("FCM 토큰 검증 중 서버 오류가 발생했습니다. errorCode={}", errorCode, e);
            return TokenValidationResult.UNAVAILABLE;
        }
    }

    // 리마인드 알람 발송 시 재사용할 실제 푸시 발송 메서드
    public void send(String fcmToken, String title, String body, Map<String, String> data) {
        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("Firebase가 초기화되지 않아 푸시를 발송할 수 없습니다.");
            return;
        }

        Message.Builder messageBuilder = Message.builder()
                .setToken(fcmToken)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build());

        if (data != null && !data.isEmpty()) {
            messageBuilder.putAllData(data);
        }

        try {
            FirebaseMessaging.getInstance().send(messageBuilder.build());
        } catch (FirebaseMessagingException e) {
            log.error("FCM 푸시 발송에 실패했습니다. errorCode={}", e.getMessagingErrorCode(), e);
        }
    }
}
