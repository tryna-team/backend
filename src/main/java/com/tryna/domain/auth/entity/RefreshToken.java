package com.tryna.domain.auth.entity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

// CHECK_TOKEN: redis 테이블임을 명시하기 위해 refresh_token -> token_redis로 변경. 또한 fcm_token 역시 해당 테이블에 저장될 예정
// 테이블 명으로 유지할지, refresh_token 으로 변경할지 확인 필요.
@RedisHash("token_redis")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    private String id;

    private String refreshToken;

    @TimeToLive
    private Long expiration;

    // TODO_FCM: fcm_token 추가

    public static RefreshToken create(Long userId, String provider, String token, long ttlSeconds) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.id = userId + ":" + provider;
        refreshToken.refreshToken = token;
        refreshToken.expiration = ttlSeconds;
        return refreshToken;
    }
}
