package com.tryna.domain.auth.service;

import com.tryna.domain.auth.enums.Provider;

public interface OAuthClient {
    // 자신이 담당하는 소셜 로그인 타입인지 확인
    boolean isSupported(Provider provider);

    /**
     * 외부 소셜 서버와 통신하여 액세스 토큰의 유효성을 검증하고,
     * 사용자 고유 ID(socialId)와 이메일 정보를 반환합니다.
     */
    SocialUserProfile getProfile(String oauthAccessToken);

    // 검증 결과 레코드
    record SocialUserProfile(String socialId, String email, String grantedScopes) {}
}