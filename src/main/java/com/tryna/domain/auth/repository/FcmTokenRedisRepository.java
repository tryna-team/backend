package com.tryna.domain.auth.repository;

import com.tryna.domain.auth.constants.RedisKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class FcmTokenRedisRepository {

    private final StringRedisTemplate stringRedisTemplate;

    public void add(Long userId, String fcmToken, Duration ttl) {
        if (!StringUtils.hasText(fcmToken)) {
            return;
        }

        String key = RedisKey.fcmTokens(userId);
        stringRedisTemplate.opsForSet().add(key, fcmToken);
        stringRedisTemplate.expire(key, ttl);
    }

    public void remove(Long userId, String fcmToken) {
        if (!StringUtils.hasText(fcmToken)) {
            return;
        }

        stringRedisTemplate.opsForSet().remove(RedisKey.fcmTokens(userId), fcmToken);
    }

    public Set<String> findAll(Long userId) {
        Set<String> members = stringRedisTemplate.opsForSet().members(RedisKey.fcmTokens(userId));
        return members == null ? Collections.emptySet() : members;
    }

    public void refreshTtl(Long userId, Duration ttl) {
        stringRedisTemplate.expire(RedisKey.fcmTokens(userId), ttl);
    }

    public void deleteAllByUserId(Long userId) {
        stringRedisTemplate.delete(RedisKey.fcmTokens(userId));
    }
}
