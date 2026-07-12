package com.tryna.domain.auth.service;

import com.tryna.domain.auth.dto.GoogleUserInfoResponse;
import com.tryna.domain.auth.enums.Provider;
import com.tryna.global.exception.AuthErrorCode;
import com.tryna.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class GoogleOAuthClient implements OAuthClient {

    private final RestTemplate restTemplate;

    // YAML에서 주입받은 우리 서버의 Client ID
    @Value("${oauth2.google.client-id}")
    private String googleClientId;

    private static final String TOKEN_INFO_URL = "https://oauth2.googleapis.com/tokeninfo?access_token=";
    private static final String USER_INFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";

    @Override
    public boolean isSupported(Provider provider) {
        return provider == Provider.GOOGLE;
    }

    @Override
    public SocialUserProfile getProfile(String oauthAccessToken) {
        // 1. 토큰 유효성 및 발급처(Audience) 교차 검증
        verifyTokenAudience(oauthAccessToken);

        // 2. 유저 정보 조회
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(oauthAccessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<GoogleUserInfoResponse> response;

        // [통신 영역]: 외부 구글 서버와의 통신만 예외 처리
        try {
            response = restTemplate.exchange(USER_INFO_URL, HttpMethod.GET, request, GoogleUserInfoResponse.class);
        } catch (HttpClientErrorException e) {
            throw new BusinessException(AuthErrorCode.AUTH_401_INVALID_TOKEN);
        } catch (Exception e) {
            throw new BusinessException(AuthErrorCode.A105_AUTH_SESSION_400);
        }

        // [비즈니스 검증 영역]: 정상 통신 후 데이터 검증
        GoogleUserInfoResponse userInfo = response.getBody();
        if (userInfo == null || userInfo.id() == null) {
            throw new BusinessException(AuthErrorCode.AUTH_401_INVALID_TOKEN);
        }

        return new SocialUserProfile(userInfo.id(), userInfo.email());
    }

    //  해커의 토큰 탈취 및 우회 공격 방어 로직
    private void verifyTokenAudience(String accessToken) {
        ResponseEntity<Map> response;

        // [통신 영역]: 외부 구글 서버와의 통신만 예외 처리
        try {
            response = restTemplate.getForEntity(TOKEN_INFO_URL + accessToken, Map.class);
        } catch (HttpClientErrorException e) {
            // 4xx 에러 (구글이 토큰을 거절함)
            throw new BusinessException(AuthErrorCode.AUTH_401_INVALID_TOKEN);
        } catch (Exception e) {
            // 5xx 타임아웃, DNS 등 네트워크 통신 장애
            throw new BusinessException(AuthErrorCode.A105_AUTH_SESSION_400); // (서버 에러용 코드가 있다면 그걸로 변경 권장)
        }

        // [비즈니스 검증 영역]: 통신 완료 후 응답값 검증
        if (response.getBody() == null) {
            throw new BusinessException(AuthErrorCode.AUTH_401_INVALID_TOKEN);
        }

        String aud = (String) response.getBody().get("aud");

        // 구글이 발급한 토큰의 도착지(aud)가 우리 프로젝트의 Client ID와 다르면 예외 처리
        if (!googleClientId.equals(aud)) {
            throw new BusinessException(AuthErrorCode.AUTH_401_INVALID_TOKEN);
        }
    }
}