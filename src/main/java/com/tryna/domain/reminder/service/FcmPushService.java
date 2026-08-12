package com.tryna.domain.reminder.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
            log.warn("Firebase가 초기화되지 않아 FCM 토큰을 검증할 수 없습니다.");
            return TokenValidationResult.UNAVAILABLE;
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

    public enum DeliveryResult {
        SUCCESS,
        TRANSIENT_FAILURE,
        PERMANENT_FAILURE,
        UNREGISTERED,
        UNAVAILABLE
    }

    public record TokenDeliveryOutcome(
            String token,
            DeliveryResult result
    ) {
    }

    // FCM sendEachForMulticast는 요청당 최대 500개 토큰만 허용한다.
    private static final int MAX_TOKENS_PER_BATCH = 500;

    public List<TokenDeliveryOutcome> sendToTokens(
            Collection<String> tokens,
            String title,
            String body,
            Map<String, String> data
    ) {
        if (tokens == null || tokens.isEmpty()) {
            return List.of();
        }

        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("Firebase가 초기화되지 않아 푸시를 발송할 수 없습니다.");
            return tokens.stream()
                    .map(token -> new TokenDeliveryOutcome(token, DeliveryResult.UNAVAILABLE))
                    .toList();
        }

        List<String> tokenList = new ArrayList<>(tokens);
        List<TokenDeliveryOutcome> outcomes = new ArrayList<>(tokenList.size());

        for (int start = 0; start < tokenList.size(); start += MAX_TOKENS_PER_BATCH) {
            int end = Math.min(start + MAX_TOKENS_PER_BATCH, tokenList.size());
            outcomes.addAll(sendBatch(tokenList.subList(start, end), title, body, data));
        }

        return outcomes;
    }

    private List<TokenDeliveryOutcome> sendBatch(
            List<String> tokens,
            String title,
            String body,
            Map<String, String> data
    ) {
        MulticastMessage.Builder messageBuilder = MulticastMessage.builder()
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .addAllTokens(tokens);

        if (data != null && !data.isEmpty()) {
            messageBuilder.putAllData(data);
        }

        try {
            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(messageBuilder.build());
            List<TokenDeliveryOutcome> outcomes = new ArrayList<>(tokens.size());

            for (int i = 0; i < response.getResponses().size(); i++) {
                String token = tokens.get(i);
                SendResponse sendResponse = response.getResponses().get(i);

                if (sendResponse.isSuccessful()) {
                    outcomes.add(new TokenDeliveryOutcome(token, DeliveryResult.SUCCESS));
                    continue;
                }

                FirebaseMessagingException exception = sendResponse.getException();
                DeliveryResult result = mapDeliveryResult(exception);
                logDeliveryFailure(result, exception);
                outcomes.add(new TokenDeliveryOutcome(token, result));
            }

            return outcomes;
        } catch (Exception e) {
            log.error("FCM multicast 발송 중 오류가 발생했습니다.", e);
            return tokens.stream()
                    .map(token -> new TokenDeliveryOutcome(token, DeliveryResult.TRANSIENT_FAILURE))
                    .toList();
        }
    }

    // 리마인드 알람 발송 시 재사용할 실제 푸시 발송 메서드
    public DeliveryResult send(String fcmToken, String title, String body, Map<String, String> data) {
        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("Firebase가 초기화되지 않아 푸시를 발송할 수 없습니다.");
            return DeliveryResult.UNAVAILABLE;
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
            return DeliveryResult.SUCCESS;
        } catch (FirebaseMessagingException e) {
            DeliveryResult result = mapDeliveryResult(e);
            logDeliveryFailure(result, e);
            return result;
        }
    }

    private DeliveryResult mapDeliveryResult(FirebaseMessagingException exception) {
        if (exception == null) {
            return DeliveryResult.TRANSIENT_FAILURE;
        }

        MessagingErrorCode errorCode = exception.getMessagingErrorCode();
        if (errorCode == MessagingErrorCode.UNREGISTERED
                || errorCode == MessagingErrorCode.SENDER_ID_MISMATCH) {
            return DeliveryResult.UNREGISTERED;
        }
        // INVALID_ARGUMENT는 토큰이 아닌 요청(payload) 문제일 수 있으므로 토큰 등록 해제와 구분한다.
        if (errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
            return DeliveryResult.PERMANENT_FAILURE;
        }

        return DeliveryResult.TRANSIENT_FAILURE;
    }

    private void logDeliveryFailure(DeliveryResult result, FirebaseMessagingException exception) {
        MessagingErrorCode errorCode = exception != null ? exception.getMessagingErrorCode() : null;
        switch (result) {
            case UNREGISTERED -> log.warn("등록 해제된 FCM 토큰입니다. errorCode={}", errorCode);
            case PERMANENT_FAILURE -> log.warn("FCM 요청 payload가 유효하지 않습니다. errorCode={}", errorCode);
            default -> log.error("FCM 푸시 발송에 실패했습니다. errorCode={}", errorCode, exception);
        }
    }
}
