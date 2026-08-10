package com.tryna.domain.recommendation.repository;

import com.tryna.domain.recommendation.constants.RecommendationRedisKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class RecommendationRevisionRedisRepository {

    private static final long TTL_SECONDS = Duration.ofHours(24).toSeconds();

    private static final DefaultRedisScript<Long> SAVE_IF_LATEST_SCRIPT =
            new DefaultRedisScript<>("""
                local current = redis.call('GET', KEYS[1])
                local incoming = tonumber(ARGV[1])

                if not current or incoming >= tonumber(current) then
                    redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
                    return 1
                end

                return 0
                """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public boolean saveIfLatest(
            String tempEventId,
            Integer draftRevision
    ) {
        String key = RecommendationRedisKey.latestRevision(tempEventId);

        Long result = redisTemplate.execute(
                SAVE_IF_LATEST_SCRIPT,
                List.of(key),
                draftRevision.toString(),
                Long.toString(TTL_SECONDS)
        );

        return Long.valueOf(1L).equals(result);
    }
}
