package com.tryna.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

// ElastiCache/Valkey 전송 중 암호화(TLS) 연동 설정.
// 저장 중 암호화(at-rest)는 AWS가 처리하므로 애플리케이션 설정이 필요 없다.
// TLS는 Spring Data Redis의 {@code spring.data.redis.ssl.enabled} 한 가지로 활성화한다.
@Configuration
@ConditionalOnProperty(name = "spring.data.redis.ssl.enabled", havingValue = "true")
public class RedisConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port,
            @Value("${VALKEY_PASSWORD:#{null}}") String password
    ) {
        RedisStandaloneConfiguration standaloneConfig = new RedisStandaloneConfiguration(host, port);
        if (password != null && !password.isBlank()) {
            standaloneConfig.setPassword(password);
        }

        // ElastiCache는 AWS 관리 인증서를 사용하므로 peer verification을 비활성화한다.
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(2))
                .useSsl()
                .disablePeerVerification()
                .build();

        return new LettuceConnectionFactory(standaloneConfig, clientConfig);
    }
}
