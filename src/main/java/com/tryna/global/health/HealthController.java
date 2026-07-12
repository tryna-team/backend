package com.tryna.global.health;

import com.tryna.global.health.dto.ComponentHealth;
import com.tryna.global.health.dto.HealthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Tag(name = "Health", description = "인프라 헬스체크")
@RestController
@RequiredArgsConstructor
@Slf4j
public class HealthController {

    private static final int DB_VALIDATION_TIMEOUT_SECONDS = 2;
    private static final int REDIS_PING_TIMEOUT_SECONDS = 2;
    private static final String REDIS_PING_RESPONSE = "PONG";
    private static final String DB_DOWN_MESSAGE = "Database connectivity check failed";
    private static final String REDIS_DOWN_MESSAGE = "Redis connectivity check failed";

    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;

    @Operation(summary = "기본 헬스체크", description = "ALB 타겟 그룹 헬스체크용. 외부 의존성 없이 서버 생존 여부만 확인한다.")
    @SecurityRequirements({})
    @GetMapping(value = "/", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("OK");
    }

    @Operation(summary = "상세 헬스체크", description = "DB(PostgreSQL), Redis 연결 상태를 포함한 상세 상태를 반환한다.")
    @SecurityRequirements({})
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        ComponentHealth database = checkDatabase();
        ComponentHealth redis = checkRedis();
        HealthResponse response = HealthResponse.of(database, redis);

        return response.isUp()
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    private ComponentHealth checkDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(DB_VALIDATION_TIMEOUT_SECONDS)
                    ? ComponentHealth.up()
                    : ComponentHealth.down(DB_DOWN_MESSAGE);
        } catch (SQLException e) {
            log.warn("[Health] DB 헬스체크 실패", e);
            return ComponentHealth.down(DB_DOWN_MESSAGE);
        }
    }

    private ComponentHealth checkRedis() {
        CompletableFuture<String> pingFuture = CompletableFuture
                .supplyAsync(() -> redisTemplate.execute((RedisCallback<String>) RedisConnection::ping));
        try {
            String pong = pingFuture.get(REDIS_PING_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (REDIS_PING_RESPONSE.equalsIgnoreCase(pong)) {
                return ComponentHealth.up();
            }
            log.warn("[Health] Redis 헬스체크 실패: unexpected ping response={}", pong);
            return ComponentHealth.down(REDIS_DOWN_MESSAGE);
        } catch (TimeoutException e) {
            pingFuture.cancel(true);
            log.warn("[Health] Redis 헬스체크 타임아웃 ({}s)", REDIS_PING_TIMEOUT_SECONDS);
            return ComponentHealth.down(REDIS_DOWN_MESSAGE);
        } catch (InterruptedException e) {
            pingFuture.cancel(true);
            Thread.currentThread().interrupt();
            log.warn("[Health] Redis 헬스체크 중단", e);
            return ComponentHealth.down(REDIS_DOWN_MESSAGE);
        } catch (ExecutionException e) {
            log.warn("[Health] Redis 헬스체크 실패", e.getCause());
            return ComponentHealth.down(REDIS_DOWN_MESSAGE);
        }
    }
}
