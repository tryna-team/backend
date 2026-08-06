package com.tryna.domain.auth.service;

import com.tryna.domain.action.repository.ActionItemsRepository;
import com.tryna.domain.auth.dto.*;
import com.tryna.domain.auth.entity.Auths;
import com.tryna.domain.auth.enums.PermissionAction;
import com.tryna.domain.auth.enums.Provider;
import com.tryna.domain.auth.repository.AuthsRepository;
import com.tryna.domain.auth.repository.FcmTokenRedisRepository;
import com.tryna.domain.auth.repository.SessionRedisRepository;
import com.tryna.domain.event.repository.EventAnalysisLogsRepository;
import com.tryna.domain.event.repository.EventsRepository;
import com.tryna.domain.event.repository.UserEventsRepository;
import com.tryna.domain.external.repository.ExternalCalendarConnectionsRepository;
import com.tryna.domain.label.repository.LabelsRepository;
import com.tryna.domain.label.service.DefaultLabelService;
import com.tryna.domain.recommendation.repository.RecommendationFeedbacksRepository;
import com.tryna.domain.reminder.repository.RemindersRepository;
import com.tryna.domain.term.entity.Terms;
import com.tryna.domain.term.enums.TermType;
import com.tryna.domain.term.entity.mapping.UserAgreedTerms;
import com.tryna.domain.term.repository.TermsRepository;
import com.tryna.domain.term.repository.UserAgreedTermsRepository;
import com.tryna.domain.user.dto.UserConversionResponse;
import com.tryna.domain.user.entity.Users;
import com.tryna.domain.user.enums.UserRole;
import com.tryna.domain.user.repository.UserRepository;
import com.tryna.domain.user.repository.UserSettingsRepository;
import com.tryna.global.exception.AuthErrorCode;
import com.tryna.global.exception.BusinessException;
import com.tryna.global.exception.UserErrorCode;
import com.tryna.global.security.jwt.JwtTokenProvider;
import com.tryna.global.security.jwt.TokenPair;
import com.tryna.global.security.jwt.TokenType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtTokenProvider jwtTokenProvider;
    private final SessionRedisRepository sessionRedisRepository;
    private final FcmTokenRedisRepository fcmTokenRedisRepository;
    private final AuthsRepository authsRepository;
    private final TermsRepository termsRepository;
    private final UserAgreedTermsRepository userAgreedTermsRepository;
    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final RemindersRepository remindersRepository;
    private final ExternalCalendarConnectionsRepository externalCalendarConnectionsRepository;
    private final ActionItemsRepository actionItemsRepository;
    private final EventsRepository eventsRepository;
    private final UserEventsRepository userEventsRepository;
    private final RecommendationFeedbacksRepository recommendationFeedbacksRepository;
    private final EventAnalysisLogsRepository eventAnalysisLogsRepository;
    private final LabelsRepository labelsRepository;
    private final OAuthClientProvider oAuthClientProvider;
    private final GoogleTokenProvider googleTokenProvider;
    private final SocialSignupService socialSignupService;
    private final DefaultLabelService defaultLabelService;
    private final org.springframework.context.ApplicationEventPublisher applicationEventPublisher;

    /**
     * A104: 로그인 필요 여부 확인
     */
    @Transactional(readOnly = true)
    public PermissionCheckResponse checkPermission(PermissionAction actionType) {
        if (actionType == null) {
            throw new BusinessException(AuthErrorCode.A104_PERMISSION_CHECK_400);
        }
        return new PermissionCheckResponse(
                actionType.isLoginRequired(),
                actionType.getGuideMessage()
        );
    }

    /**
     * A105: 소셜 로그인 및 회원가입
     */
    @Transactional
    public AuthSessionResponse socialLogin(AuthSessionCreateRequest request) {

        // 1. 소셜 서버를 통해 토큰 검증 및 유저 프로필 획득
        OAuthClient client = oAuthClientProvider.getClient(request.provider());
        OAuthClient.SocialUserProfile profile = client.getProfile(request.oauthAccessToken());
        String socialId = profile.socialId();
        String email = profile.email();
        String grantedScopes = profile.grantedScopes();

        // 2. 이미 연동된 소셜 계정인지 확인
        Optional<Auths> existingAuth = authsRepository.findByProviderAndSocialIdAndDeletedAtIsNull(request.provider(), socialId);

        Users user;
        boolean isNewUser = existingAuth.isEmpty();

        if (existingAuth.isPresent()) {
            // [A. 기존 회원 로그인]
            Auths auth = existingAuth.get();
            user = auth.getUser();

            validateOAuthRefreshTokenOwnership(request.oauthRefreshToken(), socialId, request.provider());

            if (request.oauthRefreshToken() != null || grantedScopes != null) {
                auth.updateOAuthInfo(request.oauthRefreshToken(), grantedScopes);
            }
        } else {
            // [B. 신규 회원 가입]
            validateOAuthRefreshTokenOwnership(request.oauthRefreshToken(), socialId, request.provider());

            try {
                // 독립된 트랜잭션(REQUIRES_NEW)으로 신규 가입 시도
                user = socialSignupService.registerNewUser(
                        request.provider(),
                        socialId,
                        email,
                        request.oauthRefreshToken(),
                        grantedScopes,
                        request.agreedTermTypes()
                );
            } catch (DataIntegrityViolationException e) {
                // 소셜 유니크 제약조건(uq_auths_provider_social_id_active) 위반인지 정밀 검사
                String rootMessage = e.getMostSpecificCause().getMessage();
                if (rootMessage != null && rootMessage.contains("uq_auths_provider_social_id_active")) {
                    // 동시 요청으로 인해 다른 트랜잭션이 먼저 가입시킨 경우 -> 기존 계정 로그인 흐름으로 안전하게 흡수
                    Auths concurrentAuth = authsRepository.findByProviderAndSocialIdAndDeletedAtIsNull(request.provider(), socialId)
                            .orElseThrow(() -> new BusinessException(AuthErrorCode.AUTH_409));

                    user = concurrentAuth.getUser();
                    concurrentAuth.updateOAuthInfo(request.oauthRefreshToken(), grantedScopes);
                    isNewUser = false;

                    // 동시성 충돌로 복구된 concurrentAuth를 existingAuth에 반영하여 이후 토큰 검증(hasValidToken) 정상 동작 보장
                    existingAuth = Optional.of(concurrentAuth);
                } else {
                    // 그 외의 데이터 무결성 위반(예: 기타 제약조건 에러 등)은 그대로 예외 전파
                    throw e;
                }
            }
        }

        // 3. 토큰 발급 및 Redis 세션/FCM 저장
        TokenPair tokenPair = issueSession(user.getUserId(), request.deviceId(), request.fcmToken(), user.getUserRole().name());

        // 3-1. DB 커밋 완료 후 비동기로 구글 캘린더 동기화 이벤트 발행 및 플래그 설정
        boolean syncScheduled = false;
        if (request.provider() == Provider.GOOGLE) {
            // 1) 요청 바디에 토큰이 있거나, 2) 기존 사용자(existingAuth)가 있고 그 안에 토큰이 있는지 확인
            boolean hasValidToken = (request.oauthRefreshToken() != null && !request.oauthRefreshToken().isBlank())
                    || (existingAuth.isPresent() && existingAuth.get().getOauthRefreshToken() != null && !existingAuth.get().getOauthRefreshToken().isBlank());

            if (hasValidToken) {
                final Long syncUserId = user.getUserId();
                applicationEventPublisher.publishEvent(new com.tryna.domain.external.CalendarSyncRequestedEvent(syncUserId, null));
                syncScheduled = true;
            }
        }

        return new AuthSessionResponse(
                user.getUserId(),
                user.getUserRole().name(),
                isNewUser,
                syncScheduled,
                createAuthTokenResponse(tokenPair)
        );
    }

    /**
     * A106: 회원 전환 유도 (비회원 -> 정식 회원)
     */
    @Transactional
    public UserConversionResponse convertGuestToUser(Long userId, AuthSessionCreateRequest request) {
        // 1. 기존 비회원 유저 조회 및 권한 검증
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_404));

        if (user.getUserRole() != UserRole.GUEST) {
            // 이미 정식 회원이거나 권한이 없는 경우
            throw new BusinessException(AuthErrorCode.A106_USER_CONVERSION_403);
        }

        // 2. 소셜 프로필 조회
        OAuthClient client = oAuthClientProvider.getClient(request.provider());
        OAuthClient.SocialUserProfile profile = client.getProfile(request.oauthAccessToken());
        String socialId = profile.socialId();
        String email = profile.email();
        String grantedScopes = profile.grantedScopes();

        // 3. 이미 가입된 소셜 계정인지 확인 (어뷰징 및 중복 방지)
        Optional<Auths> existingAuth = authsRepository.findByProviderAndSocialIdAndDeletedAtIsNull(request.provider(), socialId);
        if (existingAuth.isPresent()) {
            throw new BusinessException(AuthErrorCode.AUTH_409);
        }

        // 4. 필수 약관 검증
        validateRequiredTerms(request.agreedTermTypes());

        // 5. 비회원 -> 회원 승급 시 기본 라벨 존재 여부 확인 및 보장 (독립 트랜잭션 처리)
        try {
            defaultLabelService.createDefaultLabel(user);
        } catch (DataIntegrityViolationException e) {
            // REQUIRES_NEW 트랜잭션 내에서 발생한 충돌을 상위 트랜잭션(convertGuestToUser)에서 흡수
            String rootMessage = e.getMostSpecificCause().getMessage();
            if (rootMessage != null && rootMessage.contains("uq_labels_user_default_active")) {
                log.info("GUEST 회원 전환 중 기본 라벨 동시 생성 충돌 흡수 - userId: {}", user.getUserId());
            } else {
                throw e;
            }
        }

        // 6. 비회원 데이터 승급 및 guestId 초기화 (더티 체킹)
        user.upgradeToUser();

        validateOAuthRefreshTokenOwnership(request.oauthRefreshToken(), socialId, request.provider());

        // 7. Auths 정보 생성 및 약관 매핑 저장
        Auths newAuth = Auths.createAuth(user, request.provider(), socialId, email, request.oauthRefreshToken(), grantedScopes);
        try {
            authsRepository.saveAndFlush(newAuth);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(AuthErrorCode.AUTH_409);
        }

        saveUserAgreedTerms(user, request.agreedTermTypes());

        // 8. 기존 GUEST 권한의 Redis 세션 및 FCM 토큰 파기
        // 8-1. 기존 세션에서 FCM 토큰을 안전하게 조회
        Optional<String> existingFcmToken = sessionRedisRepository.findFcmToken(userId, request.deviceId());

        // 8-2. 기존 토큰이 존재한다면 FCM Set에서 확실하게 제거
        existingFcmToken.ifPresent(token -> fcmTokenRedisRepository.remove(userId, token));

        // 8-3. 안전하게 세션 Hash를 삭제
        sessionRedisRepository.delete(userId, request.deviceId());

        // 9. 새로운 정식 회원 토큰(USER) 발급 및 세션 저장
        TokenPair tokenPair = issueSession(user.getUserId(), request.deviceId(), request.fcmToken(), user.getUserRole().name());

        // 9-1. 토큰 저장 후 안전하게 외부 캘린더 동기화 트리거 (이벤트 발행)
        if (request.provider() == Provider.GOOGLE && request.oauthRefreshToken() != null && !request.oauthRefreshToken().isBlank()) {
            final Long syncUserId = user.getUserId();
            applicationEventPublisher.publishEvent(new com.tryna.domain.external.CalendarSyncRequestedEvent(syncUserId, null));
        }

        return new UserConversionResponse(
                user.getUserId(),
                user.getUserRole().name(),
                createAuthTokenResponse(tokenPair)
        );
    }

    /**
     * 로그인(GUEST/USER 공통) — session Hash 저장 + fcm Set 추가
     */
    public TokenPair issueSession(Long userId, String deviceId, String fcmToken, String scopes) {
        // 1. JWT 토큰 발급
        TokenPair tokenPair = jwtTokenProvider.generateTokenPair(userId);

        // 2. 만료 시간(TTL) 계산
        Duration ttl = Duration.ofSeconds(jwtTokenProvider.getRefreshExpirationSeconds());

        // 3. 기존 FCM 토큰 조회
        String existingFcmToken = sessionRedisRepository.findFcmToken(userId, deviceId).orElse(null);

        // 4. 최종 FCM 토큰 결정 (값이 없으면 기존 것 유지)
        String finalFcmToken = (fcmToken != null && !fcmToken.isBlank()) ? fcmToken : existingFcmToken;

        // 5. 기존 토큰과 새로운 토큰이 다를 경우, 기존 토큰을 Set에서 확실하게 제거
        if (existingFcmToken != null && !existingFcmToken.isBlank()
                && finalFcmToken != null && !finalFcmToken.equals(existingFcmToken)) {
            fcmTokenRedisRepository.remove(userId, existingFcmToken);
        }

        // 6. Redis 세션 Hash 저장
        sessionRedisRepository.save(
                userId, deviceId, tokenPair.refreshToken(), finalFcmToken, scopes, Instant.now(), ttl
        );

        // 7. 새로운 토큰을 Set에 추가
        if (finalFcmToken != null && !finalFcmToken.isBlank()) {
            fcmTokenRedisRepository.add(userId, finalFcmToken, ttl);
        }

        return tokenPair;
    }

    /**
     * A108: 토큰 갱신 (Refresh Token Rotation)
     */
    @Transactional
    public AuthTokenResponse reissue(AuthTokenRefreshRequest request) {
        String providedRefreshToken = request.refreshToken();
        String deviceId = request.deviceId();

        // 1. 리프레시 토큰 자체의 유효성(만료, 위변조) 1차 검증
        try {
            jwtTokenProvider.validateToken(providedRefreshToken, TokenType.REFRESH);
        } catch (BusinessException e) {
            throw new BusinessException(AuthErrorCode.A108_AUTH_REFRESH_401);
        }

        // 2. 토큰에서 userId 추출
        Long userId = jwtTokenProvider.getUserId(providedRefreshToken);

        // 3. Redis 세션 2차 검증 (DB에 저장된 실제 토큰과 일치하는지 확인)
        Optional<String> storedRefreshToken = sessionRedisRepository.findTokenValue(userId, deviceId);

        // 저장된 토큰이 없거나, 보낸 토큰과 다르면 -> 토큰 탈취(RTR 위반) 또는 이미 로그아웃된 상태
        if (storedRefreshToken.isEmpty() || !storedRefreshToken.get().equals(providedRefreshToken)) {
            // 보안을 위해 해당 기기의 세션을 즉시 파기
            sessionRedisRepository.delete(userId, deviceId);
            throw new BusinessException(AuthErrorCode.A108_AUTH_REFRESH_401);
        }

        // 4. 유저 상태 확인 (그 사이 탈퇴했거나 상태가 변경되었는지 검증)
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.A108_AUTH_REFRESH_401));

        // 5. 기존 FCM 토큰 유지 (Redis 세션을 덮어씌울 때 기존 FCM 토큰이 날아가지 않도록 조회)
        String existingFcmToken = sessionRedisRepository.findFcmToken(userId, deviceId).orElse(null);

        // 6. 새로운 토큰 쌍 발급 및 Redis 세션 갱신 (기존 세션은 덮어씌워짐 - Refresh Token Rotation)
        TokenPair newTokenPair = issueSession(userId, deviceId, existingFcmToken, user.getUserRole().name());

        return createAuthTokenResponse(newTokenPair);
    }

    /**
     * A109: 로그아웃
     */
    @Transactional
    public void logout(Long userId, String deviceId) {
        // 1. 기존 기기의 FCM 토큰을 Redis Hash에서 꺼내기
        String fcmToken = sessionRedisRepository.findFcmToken(userId, deviceId).orElse(null);

        // 2. FCM 토큰이 존재하면 FCM Set에서 확실하게 제거 (푸시 알림 차단)
        if (fcmToken != null && !fcmToken.isBlank()) {
            fcmTokenRedisRepository.remove(userId, fcmToken);
        }

        // 3. 마지막으로 해당 기기의 세션(Hash) 자체를 완전히 삭제
        sessionRedisRepository.delete(userId, deviceId);
    }

    /**
     * 앱 실행 시 FCM 토큰만 갱신 (session Hash + fcm Set 동기화)
     */
    @Transactional
    public void updateFcmToken(Long userId, String deviceId, String newFcmToken) {
        if (newFcmToken == null || newFcmToken.isBlank()) {
            return;
        }

        // 1. 기존 세션의 FCM 토큰 조회
        String existingFcmToken = sessionRedisRepository.findFcmToken(userId, deviceId).orElse(null);

        // 2. 토큰이 변경되지 않았다면 무시 (불필요한 Redis 통신 방지)
        if (newFcmToken.equals(existingFcmToken)) {
            return;
        }

        // 3. 기존 토큰이 존재한다면 FCM Set에서 확실하게 제거 (알림 중복 방지)
        if (existingFcmToken != null && !existingFcmToken.isBlank()) {
            fcmTokenRedisRepository.remove(userId, existingFcmToken);
        }

        // 4. Redis 세션 Hash의 FCM 필드만 업데이트
        sessionRedisRepository.updateFcmToken(userId, deviceId, newFcmToken);

        // 5. 새로운 토큰을 FCM Set에 추가 (리프레시 토큰의 만료 시간과 동일하게 연장)
        Duration ttl = Duration.ofSeconds(jwtTokenProvider.getRefreshExpirationSeconds());
        fcmTokenRedisRepository.add(userId, newFcmToken, ttl);
    }

    /**
     * G104: 회원 탈퇴 — userId 기준 session/fcm Redis 데이터 전체 삭제 후 DB 정리
     */
    @Transactional
    public void withdraw(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_404));

        LocalDateTime now = LocalDateTime.now();

        // 1. 유저 Soft Delete 상태 반영 (벌크 쿼리 실행 전 더티 체킹 반영)
        // 뒤이어 실행되는 벌크 쿼리의 flushAutomatically = true에 의해 DB에 즉시 FLUSH되고,
        // clearAutomatically = true에 의해 영속성 컨텍스트가 비워져도 DB에는 안전하게 반영됩니다.
        user.deleteSoft();

        // 2. 조인 의존성이 있는 쿼리를 무조건 먼저 실행 (순서 중요)
        eventAnalysisLogsRepository.deleteByUserId(userId);
        actionItemsRepository.softDeleteByUserId(userId, now);
        eventsRepository.softDeleteByUserId(userId, now);

        // 3. Hard Delete 대상 물리 삭제
        remindersRepository.deleteByUserId(userId);
        recommendationFeedbacksRepository.deleteByUserId(userId);
        userEventsRepository.deleteByUserId(userId);
        userAgreedTermsRepository.deleteByUserId(userId);
        externalCalendarConnectionsRepository.deleteByUserId(userId);

        // 4. 나머지 Soft Delete 대상 논리 삭제 (flush & clear 수행)
        labelsRepository.softDeleteByUserId(userId, now);
        userSettingsRepository.softDeleteByUserId(userId, now);
        authsRepository.softDeleteByUserId(userId, now);

        // 5. DB 트랜잭션이 완벽히 커밋된 직후에만 Redis 세션 및 FCM 토큰 파기 수행
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sessionRedisRepository.deleteAllByUserId(userId);
                fcmTokenRedisRepository.deleteAllByUserId(userId);
                log.info("유저 ID {}의 회원 탈퇴 완료 및 Redis 세션/FCM 토큰이 안전하게 파기되었습니다.", userId);
            }
        });
    }

    // --- Helper Method ---

    private AuthTokenResponse createAuthTokenResponse(TokenPair tokenPair) {
        String refreshTokenExpiresAt = Instant.now()
                .plusSeconds(jwtTokenProvider.getRefreshExpirationSeconds())
                .toString();

        return new AuthTokenResponse(
                "Bearer",
                tokenPair.accessToken(),
                jwtTokenProvider.getAccessExpirationSeconds(),
                tokenPair.refreshToken(),
                refreshTokenExpiresAt
        );
    }

    // --- Terms Helper Methods ---

    // 필수 약관 검증 공통 메서드
    private void validateRequiredTerms(List<TermType> agreedTypes) {
        List<TermType> types = (agreedTypes != null) ? agreedTypes : List.of();
        List<TermType> requiredTypes = termsRepository.findRequiredTermTypes();

        if (!types.containsAll(requiredTypes)) {
            throw new BusinessException(AuthErrorCode.TERMS_400);
        }
    }

    // 약관 동의 이력 저장 공통 메서드
    private void saveUserAgreedTerms(Users user, List<TermType> agreedTypes) {
        List<TermType> types = (agreedTypes != null) ? agreedTypes : List.of();

        if (!types.isEmpty()) {
            List<Terms> latestTerms = termsRepository.findLatestTermsByTypes(types);
            List<UserAgreedTerms> agreedTermsList = latestTerms.stream()
                    .map(term -> UserAgreedTerms.create(user, term))
                    .toList();
            userAgreedTermsRepository.saveAll(agreedTermsList);
        }
    }

    // 프론트엔드가 보낸 Refresh Token이 Access Token의 주인과 일치하는지 교차 검증
    private void validateOAuthRefreshTokenOwnership(String refreshToken, String expectedSocialId, Provider provider) {
        if (refreshToken == null || refreshToken.isBlank() || provider != Provider.GOOGLE) {
            return;
        }

        try {
            // 프론트가 준 Refresh Token을 이용해 구글에서 1회용 새 Access Token 발급
            String tempAccessToken = googleTokenProvider.getFreshAccessToken(refreshToken);

            // 주입된 OAuthClientProvider를 통해 클라이언트 구현체를 가져와 유저 정보 조회
            OAuthClient client = oAuthClientProvider.getClient(provider);
            OAuthClient.SocialUserProfile tempProfile = client.getProfile(tempAccessToken);

            if (!expectedSocialId.equals(tempProfile.socialId())) {
                throw new BusinessException(AuthErrorCode.AUTH_401_INVALID_TOKEN);
            }
        } catch (BusinessException e) {
            // 구글 서버 설정 오류, 인프라 장애 등 500 에러는 401로 왜곡시키지 않고 그대로 전파
            if (e.getErrorCode() == com.tryna.global.exception.CommonErrorCode.COMMON_500) {
                throw e;
            }
            // 그 외 토큰 만료/유효하지 않음 등의 비즈니스 예외는 401로 처리
            throw new BusinessException(AuthErrorCode.AUTH_401_INVALID_TOKEN);
        } catch (Exception e) {
            // 예상치 못한 시스템/네트워크 예외는 500 에러로 안전하게 처리
            log.error("구글 리프레시 토큰 교차 검증 중 시스템 오류 발생: {}", e.getMessage(), e);
            throw new BusinessException(com.tryna.global.exception.CommonErrorCode.COMMON_500);
        }
    }
}
