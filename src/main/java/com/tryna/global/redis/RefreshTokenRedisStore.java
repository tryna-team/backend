package com.tryna.global.redis;

import com.tryna.domain.auth.entity.RefreshToken;
import com.tryna.domain.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// 키 형식: {@code token_redis:{userId}:{provider}} (예: {@code token_redis:42:GUEST}, {@code token_redis:42:KAKAO})
@Component
@RequiredArgsConstructor
public class RefreshTokenRedisStore {

    private static final String KEY_PREFIX = "token_redis:";

    private final RefreshTokenRepository refreshTokenRepository;
    private final StringRedisTemplate stringRedisTemplate;

    // refresh token을 Redis에 저장한다. 동일 userId + provider 키가 있으면 덮어쓴다.
    // TTL은 JWT refresh 만료 시간과 동일하게 맞춘다.
    public void save(Long userId, String provider, String token, Duration ttl) {
        refreshTokenRepository.save(
                RefreshToken.create(userId, provider, token, ttl.toSeconds()));
    }

    // 저장된 refresh token 문자열을 조회한다.
    // 토큰 재발급 시 JWT와 Redis 저장값 일치 여부를 확인할 때 사용
    public Optional<String> find(Long userId, String provider) {
        return refreshTokenRepository.findById(userId + ":" + provider)
                .map(RefreshToken::getRefreshToken);
    }

    // 특정 provider의 refresh token 하나만 삭제
    public void delete(Long userId, String provider) {
        refreshTokenRepository.deleteById(userId + ":" + provider);
    }

    // userId의 모든 provider refresh token을 일괄 삭제
    // KEYS 대신 SCAN을 사용해 운영 환경에서 Redis를 블로킹하지 않는다.
    public void deleteAll(Long userId) {
        ScanOptions options = ScanOptions.scanOptions()
                .match(KEY_PREFIX + userId + ":*")
                .count(100)
                .build();

        List<String> keysToDelete = stringRedisTemplate.execute(connection -> {
            List<String> keys = new ArrayList<>();
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                cursor.forEachRemaining(
                        key -> keys.add(new String(key, StandardCharsets.UTF_8)));
            }
            return keys;
        }, true);

        if (keysToDelete != null && !keysToDelete.isEmpty()) {
            stringRedisTemplate.delete(keysToDelete);
        }
    }
}
