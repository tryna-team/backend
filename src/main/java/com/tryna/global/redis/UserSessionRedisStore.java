package com.tryna.global.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// 사용자 기기별 세션(Hash) 저장소.
@Component
@RequiredArgsConstructor
public class UserSessionRedisStore {

    private final StringRedisTemplate stringRedisTemplate;

    public void save(
            Long userId,
            String deviceId,
            String tokenValue,
            String fcmToken,
            String scopes,
            Instant createdAt,
            Duration ttl
    ) {
        String key = RedisKey.session(userId, deviceId);
        stringRedisTemplate.opsForHash().putAll(key, Map.of(
                SessionHashField.TOKEN_VALUE, tokenValue,
                SessionHashField.FCM_TOKEN, nullToEmpty(fcmToken),
                SessionHashField.SCOPES, nullToEmpty(scopes),
                SessionHashField.CREATED_AT, createdAt.toString()
        ));
        stringRedisTemplate.expire(key, ttl);
    }

    public Optional<String> findTokenValue(Long userId, String deviceId) {
        return findHashField(userId, deviceId, SessionHashField.TOKEN_VALUE);
    }

    public Optional<String> findFcmToken(Long userId, String deviceId) {
        return findHashField(userId, deviceId, SessionHashField.FCM_TOKEN);
    }

    public void updateTokenValue(Long userId, String deviceId, String tokenValue, Duration ttl) {
        String key = RedisKey.session(userId, deviceId);
        stringRedisTemplate.opsForHash().put(key, SessionHashField.TOKEN_VALUE, tokenValue);
        stringRedisTemplate.expire(key, ttl);
    }

    public void updateFcmToken(Long userId, String deviceId, String fcmToken) {
        stringRedisTemplate.opsForHash().put(
                RedisKey.session(userId, deviceId),
                SessionHashField.FCM_TOKEN,
                nullToEmpty(fcmToken)
        );
    }

    public void delete(Long userId, String deviceId) {
        stringRedisTemplate.delete(RedisKey.session(userId, deviceId));
    }

    public void deleteAllByUserId(Long userId) {
        ScanOptions options = ScanOptions.scanOptions()
                .match(RedisKey.sessionPattern(userId))
                .count(100)
                .build();

        List<String> keysToDelete = stringRedisTemplate.execute(connection -> {
            List<String> keys = new ArrayList<>();
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                cursor.forEachRemaining(key -> keys.add(new String(key, StandardCharsets.UTF_8)));
            }
            return keys;
        }, true);

        if (keysToDelete != null && !keysToDelete.isEmpty()) {
            stringRedisTemplate.delete(keysToDelete);
        }
    }

    private Optional<String> findHashField(Long userId, String deviceId, String field) {
        Object value = stringRedisTemplate.opsForHash().get(RedisKey.session(userId, deviceId), field);
        if (value == null) {
            return Optional.empty();
        }
        String text = value.toString();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
