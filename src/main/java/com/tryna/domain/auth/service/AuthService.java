package com.tryna.domain.auth.service;

import com.tryna.domain.auth.dto.*;
import com.tryna.domain.auth.entity.Auths;
import com.tryna.domain.auth.enums.PermissionAction;
import com.tryna.domain.auth.repository.AuthsRepository;
import com.tryna.domain.auth.repository.FcmTokenRedisRepository;
import com.tryna.domain.auth.repository.SessionRedisRepository;
import com.tryna.domain.term.entity.Terms;
import com.tryna.domain.term.entity.mapping.UserAgreedTerms;
import com.tryna.domain.term.repository.TermsRepository;
import com.tryna.domain.term.repository.UserAgreedTermsRepository;
import com.tryna.domain.user.dto.UserConversionResponse;
import com.tryna.domain.user.entity.UserSettings;
import com.tryna.domain.user.entity.Users;
import com.tryna.domain.user.repository.UserRepository;
import com.tryna.domain.user.repository.UserSettingsRepository;
import com.tryna.global.exception.AuthErrorCode;
import com.tryna.global.exception.BusinessException;
import com.tryna.global.security.jwt.JwtTokenProvider;
import com.tryna.global.security.jwt.TokenPair;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
    private final OAuthClientProvider oAuthClientProvider;

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
        // 클라이언트가 이메일을 별도로 넘겨줬다면 우선 사용, 없으면 소셜 서버에서 받은 이메일 사용
        String email = (request.email() != null && !request.email().isBlank()) ? request.email() : profile.email();

        // 2. 이미 연동된 소셜 계정인지 확인
        Optional<Auths> existingAuth = authsRepository.findByProviderAndSocialIdAndDeletedAtIsNull(request.provider(), socialId);

        Users user = null;
        boolean isNewUser = false;

        if (existingAuth.isPresent()) {
            // [A. 기존 회원 로그인]
            user = existingAuth.get().getUser();
        } else {
            // [B. 신규 회원 가입]
            isNewUser = true;

            // B-1. 필수 약관 검증
            validateRequiredTerms(request.agreedTermTypes());

            // B-2. Users 엔티티 생성 및 저장
            Users newUser = Users.createUser();
            user = userRepository.save(newUser);

            // B-3. UserSettings 기본 설정 생성 및 저장
            UserSettings defaultSettings = UserSettings.createDefault(user);
            userSettingsRepository.save(defaultSettings);

            //B-4. Auths 인증 정보 저장
            Auths newAuth = Auths.createAuth(user, request.provider(), socialId, email);
            try {
                authsRepository.saveAndFlush(newAuth); // 즉시 쿼리를 날려 유니크 제약조건 위반을 확인
            } catch (DataIntegrityViolationException e) {
                // DB 유니크 제약조건 위반 시 409 비즈니스 에러로 래핑하여 던짐
                throw new com.tryna.global.exception.BusinessException(com.tryna.global.exception.AuthErrorCode.AUTH_409);
            }

            // B-5. 최신 약관 매핑 및 동의 이력 저장
            saveUserAgreedTerms(user, request.agreedTermTypes());
        }

        // 3. 토큰 발급 및 Redis 세션/FCM 저장
        TokenPair tokenPair = issueSession(user.getUserId(), request.deviceId(), request.fcmToken(), user.getUserRole().name());

        return new AuthSessionResponse(
                user.getUserId(),
                user.getUserRole().name(),
                isNewUser,
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
                .orElseThrow(() -> new com.tryna.global.exception.BusinessException(com.tryna.global.exception.UserErrorCode.USER_404));

        if (user.getUserRole() != com.tryna.domain.user.enums.UserRole.GUEST) {
            // 이미 정식 회원이거나 권한이 없는 경우
            throw new com.tryna.global.exception.BusinessException(com.tryna.global.exception.AuthErrorCode.A106_USER_CONVERSION_403);
        }

        // 2. 소셜 프로필 조회
        OAuthClient client = oAuthClientProvider.getClient(request.provider());
        OAuthClient.SocialUserProfile profile = client.getProfile(request.oauthAccessToken());
        String socialId = profile.socialId();
        String email = (request.email() != null && !request.email().isBlank()) ? request.email() : profile.email();

        // 3. 이미 가입된 소셜 계정인지 확인 (어뷰징 및 중복 방지)
        Optional<Auths> existingAuth = authsRepository.findByProviderAndSocialIdAndDeletedAtIsNull(request.provider(), socialId);
        if (existingAuth.isPresent()) {
            throw new com.tryna.global.exception.BusinessException(com.tryna.global.exception.AuthErrorCode.AUTH_409);
        }

        // 4. 필수 약관 검증
        validateRequiredTerms(request.agreedTermTypes());

        // 5. 비회원 데이터 승급 및 guestId 초기화 (더티 체킹)
        user.upgradeToUser();

        // 6. Auths 정보 생성 및 약관 매핑 저장
        Auths newAuth = Auths.createAuth(user, request.provider(), socialId, email);
        try {
            authsRepository.saveAndFlush(newAuth); // 즉시 쿼리를 날려 유니크 제약조건 위반을 확인
        } catch (DataIntegrityViolationException e) {
            // DB 유니크 제약조건 위반 시 409 비즈니스 에러로 래핑하여 던짐
            throw new com.tryna.global.exception.BusinessException(com.tryna.global.exception.AuthErrorCode.AUTH_409);
        }

        saveUserAgreedTerms(user, request.agreedTermTypes());

        // 7. 기존 GUEST 권한의 Redis 세션 및 FCM 토큰 파기
        // 7-1. 기존 세션에서 FCM 토큰을 안전하게 조회
        Optional<String> existingFcmToken = sessionRedisRepository.findFcmToken(userId, request.deviceId());

        // 7-2. 기존 토큰이 존재한다면 FCM Set에서 확실하게 제거
        existingFcmToken.ifPresent(token -> fcmTokenRedisRepository.remove(userId, token));

        // 7-3. 안전하게 세션 Hash를 삭제
        sessionRedisRepository.delete(userId, request.deviceId());

        // 8. 새로운 정식 회원 토큰(USER) 발급 및 세션 저장
        TokenPair tokenPair = issueSession(user.getUserId(), request.deviceId(), request.fcmToken(), user.getUserRole().name());

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
        long refreshExpirationSeconds = jwtTokenProvider.getRefreshExpirationSeconds();
        Duration ttl = Duration.ofSeconds(refreshExpirationSeconds);

        // 3. 기존 FCM 토큰 조회
        String existingFcmToken = sessionRedisRepository.findFcmToken(userId, deviceId).orElse(null);

        // 4. 최종 FCM 토큰 결정 (값이 없으면 기존 것 유지)
        String finalFcmToken = (fcmToken != null && !fcmToken.isBlank())
                ? fcmToken
                : existingFcmToken;

        // 5. [핵심] 기존 토큰과 새로운 토큰이 다를 경우, 기존 토큰을 Set에서 확실하게 제거
        if (existingFcmToken != null && !existingFcmToken.isBlank()
                && finalFcmToken != null && !finalFcmToken.equals(existingFcmToken)) {
            fcmTokenRedisRepository.remove(userId, existingFcmToken);
        }

        // 6. Redis 세션 Hash 저장
        sessionRedisRepository.save(
                userId,
                deviceId,
                tokenPair.refreshToken(),
                finalFcmToken,
                scopes,
                Instant.now(),
                ttl
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
            jwtTokenProvider.validateToken(providedRefreshToken, com.tryna.global.security.jwt.TokenType.REFRESH);
        } catch (com.tryna.global.exception.BusinessException e) {
            throw new com.tryna.global.exception.BusinessException(com.tryna.global.exception.AuthErrorCode.A108_AUTH_REFRESH_401);
        }

        // 2. 토큰에서 userId 추출
        Long userId = jwtTokenProvider.getUserId(providedRefreshToken);

        // 3. Redis 세션 2차 검증 (DB에 저장된 실제 토큰과 일치하는지 확인)
        Optional<String> storedRefreshToken = sessionRedisRepository.findTokenValue(userId, deviceId);

        // 저장된 토큰이 없거나, 보낸 토큰과 다르면 -> 토큰 탈취(RTR 위반) 또는 이미 로그아웃된 상태
        if (storedRefreshToken.isEmpty() || !storedRefreshToken.get().equals(providedRefreshToken)) {
            // 보안을 위해 해당 기기의 세션을 즉시 파기합니다.
            sessionRedisRepository.delete(userId, deviceId);
            throw new com.tryna.global.exception.BusinessException(com.tryna.global.exception.AuthErrorCode.A108_AUTH_REFRESH_401);
        }

        // 4. 유저 상태 확인 (그 사이 탈퇴했거나 상태가 변경되었는지 검증)
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new com.tryna.global.exception.BusinessException(com.tryna.global.exception.AuthErrorCode.A108_AUTH_REFRESH_401));

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

    // TODO: 앱 실행 시 FCM 토큰만 갱신 (session Hash + fcm Set 동기화)
    public void updateFcmToken(Long userId, String deviceId, String fcmToken) {
        throw new UnsupportedOperationException("Not implemented");
    }

    // TODO: 회원 탈퇴 — userId 기준 session/fcm Redis 데이터 전체 삭제 후 DB 정리
    public void withdraw(Long userId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    // --- Helper Method ---
    private AuthTokenResponse createAuthTokenResponse(TokenPair tokenPair) {
        String refreshTokenExpiresAt = java.time.Instant.now()
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

    // 1. 필수 약관 검증 공통 메서드
    private void validateRequiredTerms(List<com.tryna.domain.term.enums.TermType> agreedTypes) {
        List<com.tryna.domain.term.enums.TermType> types = (agreedTypes != null) ? agreedTypes : List.of();
        List<com.tryna.domain.term.enums.TermType> requiredTypes = termsRepository.findRequiredTermTypes();

        if (!types.containsAll(requiredTypes)) {
            throw new com.tryna.global.exception.BusinessException(com.tryna.global.exception.AuthErrorCode.TERMS_400);
        }
    }

    // 2. 약관 동의 이력 저장 공통 메서드
    private void saveUserAgreedTerms(Users user, List<com.tryna.domain.term.enums.TermType> agreedTypes) {
        List<com.tryna.domain.term.enums.TermType> types = (agreedTypes != null) ? agreedTypes : List.of();

        if (!types.isEmpty()) {
            List<Terms> latestTerms = termsRepository.findLatestTermsByTypes(types);
            List<UserAgreedTerms> agreedTermsList = latestTerms.stream()
                    .map(term -> UserAgreedTerms.create(user, term))
                    .toList();
            userAgreedTermsRepository.saveAll(agreedTermsList);
        }
    }
}
