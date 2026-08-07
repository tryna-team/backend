package com.tryna.domain.auth.service;

import com.tryna.domain.auth.enums.Provider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("oauth-dummy")
public class DummyOAuthClient implements OAuthClient {

    @Override
    public boolean isSupported(Provider provider) {
        // 구글이 아닌 나머지(KAKAO, APPLE) 요청은 일단 다 더미로 처리
        return provider != Provider.GOOGLE;
    }

    @Override
    public SocialUserProfile getProfile(String oauthAccessToken) {
        // 토큰 값에 따라 고유한 socialId를 생성하여 계정 충돌 방지
        String socialId = (oauthAccessToken != null && !oauthAccessToken.isBlank())
                ? "dummy-social-id-" + oauthAccessToken
                : "dummy_social_id_1234";

        return new SocialUserProfile(
                socialId,
                "dummy_email@example.com",
                null
        );
    }
}