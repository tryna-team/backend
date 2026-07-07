package com.tryna.global.redis;

public final class RedisKey {

    private static final String SESSION_PREFIX = "session:user:";
    private static final String FCM_PREFIX = "fcm:user:";

    private RedisKey() {
    }

    public static String session(Long userId, String deviceId) {
        return SESSION_PREFIX + userId + ":" + deviceId;
    }

    public static String sessionPattern(Long userId) {
        return SESSION_PREFIX + userId + ":*";
    }

    public static String fcmTokens(Long userId) {
        return FCM_PREFIX + userId;
    }
}
