package com.tryna.domain.auth.service;

import com.tryna.domain.auth.enums.Provider;
import org.springframework.stereotype.Component;

@Component
public class DummyOAuthClient implements OAuthClient {

    @Override
    public SocialUserProfile getProfile(Provider provider, String oauthAccessToken) {
        // TODO: 실제 카카오/애플/구글 API 연동 전까지 통과시키기 위한 더미 데이터 반환
        return new SocialUserProfile(
                "dummy-social-id-" + oauthAccessToken,
                "dummy_" + provider.name().toLowerCase() + "@example.com"
        );
    }
}