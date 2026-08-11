package com.tryna.domain.alarm.service;

import com.tryna.domain.auth.repository.FcmTokenRedisRepository;
import com.tryna.global.exception.AlarmErrorCode;
import com.tryna.global.exception.BusinessException;
import com.tryna.global.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AlarmPushTokenService {

    private final FcmPushService fcmPushService;
    private final FcmTokenRedisRepository fcmTokenRedisRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public void registerPushToken(Long userId, String fcmPushToken) {
        FcmPushService.TokenValidationResult validationResult = fcmPushService.validateToken(fcmPushToken);

        if (validationResult == FcmPushService.TokenValidationResult.INVALID) {
            throw new BusinessException(AlarmErrorCode.F100_PUSH_TOKEN_400);
        }
        if (validationResult == FcmPushService.TokenValidationResult.UNAVAILABLE) {
            throw new BusinessException(AlarmErrorCode.F100_PUSH_TOKEN_500);
        }

        if (fcmTokenRedisRepository.findAll(userId).contains(fcmPushToken)) {
            throw new BusinessException(AlarmErrorCode.F100_PUSH_TOKEN_409);
        }

        Duration ttl = Duration.ofSeconds(jwtTokenProvider.getRefreshExpirationSeconds());
        fcmTokenRedisRepository.add(userId, fcmPushToken, ttl);
    }
}
