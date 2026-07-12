package com.tryna.domain.auth.service;

import com.tryna.domain.auth.enums.Provider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!prod")
public class DummyOAuthClient implements OAuthClient {

    @Override
    public boolean isSupported(Provider provider) {
        // 구글이 아닌 나머지(KAKAO, APPLE) 요청은 일단 다 더미로 처리
        return provider != Provider.GOOGLE;
    }

    @Override
    public SocialUserProfile getProfile(String oauthAccessToken) {
        // TODO: 실제 카카오/애플/구글 API 연동 전까지 통과시키기 위한 더미 데이터 반환
        return new SocialUserProfile(
                "dummy-social-id-" + oauthAccessToken,
                "dummy_email@example.com"
        );
    }
}