package com.tryna.global.config;

import org.springframework.context.annotation.Configuration;

// Redis 전역 설정.

// 현재는 {@code application-*.yaml}의 {@code spring.data.redis} 설정과
// Spring Boot auto-config({@code StringRedisTemplate})만으로 충분하여 Bean 정의가 없다.
// 도메인별 Redis 접근은 {@code domain/auth/repository} 등 각 bounded context에서 처리한다.
//
// 향후 이 클래스에 추가될 수 있는 설정:
//  - {@code RedisConnectionFactory} 커스터마이징 — Sentinel, Cluster, SSL/TLS
//  - {@code StringRedisTemplate} / {@code RedisTemplate} Bean — serializer, transaction 옵션 등 전역 override
//  - {@code RedisCacheManager} + {@code @EnableCaching} — API/조회 캐시 레이어 도입 시
//  - {@code RedisMessageListenerContainer} — Pub/Sub, 알림 큐 등 이벤트 기반 연동 시
@Configuration
public class RedisConfig {
}
