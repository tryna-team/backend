package com.tryna.domain.reminder.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

/**
 * Redis ZSET 기반 알람 지연 큐.
 *
 * Redisson의 RDelayedQueue 대신, 이미 프로젝트에 구성되어 있는 StringRedisTemplate만으로
 * 동일한 목적(발송 시각까지 대기 후 소비)을 달성한다.
 *
 * - member: reminderId (문자열)
 * - score : 발송 예정 시각(scheduled_at)의 epoch millis
 *
 * 발송 시각이 도래한 항목을 조회함과 동시에 큐에서 제거하는 연산을 Lua 스크립트로 원자적으로 처리하여,
 * 인스턴스가 여러 대 떠 있어도 동일한 리마인더가 중복 발송되지 않도록 한다.
 */
@Repository
@RequiredArgsConstructor
public class AlarmDelayedQueueRepository {

    private static final String QUEUE_KEY = "alarm:reminder:queue";

    private static final RedisScript<List> POP_DUE_SCRIPT = RedisScript.of("""
            local dueItems = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, ARGV[2])
            if #dueItems > 0 then
                redis.call('ZREM', KEYS[1], unpack(dueItems))
            end
            return dueItems
            """, List.class);

    private final StringRedisTemplate stringRedisTemplate;

    public void schedule(Long reminderId, LocalDateTime scheduledAt) {
        stringRedisTemplate.opsForZSet().add(QUEUE_KEY, String.valueOf(reminderId), toScore(scheduledAt));
    }

    public void cancel(Long reminderId) {
        stringRedisTemplate.opsForZSet().remove(QUEUE_KEY, String.valueOf(reminderId));
    }

    @SuppressWarnings("unchecked")
    public List<Long> popDue(int limit) {
        long nowEpochMillis = System.currentTimeMillis();

        List<String> dueReminderIds = (List<String>) stringRedisTemplate.execute(
                POP_DUE_SCRIPT,
                Collections.singletonList(QUEUE_KEY),
                String.valueOf(nowEpochMillis),
                String.valueOf(limit)
        );

        if (dueReminderIds == null || dueReminderIds.isEmpty()) {
            return List.of();
        }

        return dueReminderIds.stream().map(Long::valueOf).toList();
    }

    private double toScore(LocalDateTime scheduledAt) {
        return scheduledAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
