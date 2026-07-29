package com.tryna.domain.auth.service;

import com.tryna.global.exception.AuthErrorCode;
import com.tryna.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleTokenProvider {

    private final RestTemplate restTemplate;

    @Value("${oauth2.google.client-id}")
    private String googleClientId;

    @Value("${oauth2.google.client-secret}")
    private String googleClientSecret;

    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";

    /**
     * DB에 저장된 구글 Refresh Token을 사용해 새로운 Access Token을 발급받습니다.
     */
    public String getFreshAccessToken(String googleRefreshToken) {
        if (googleRefreshToken == null || googleRefreshToken.isBlank()) {
            throw new BusinessException(AuthErrorCode.AUTH_401_INVALID_TOKEN);
        }

        HttpHeaders headers = new HttpHeaders();
        // 구글이 요구하는 form-urlencoded 방식 설정
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", googleClientId);
        params.add("client_secret", googleClientSecret);
        params.add("refresh_token", googleRefreshToken);
        params.add("grant_type", "refresh_token");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(GOOGLE_TOKEN_URL, request, Map.class);
            Map<String, Object> body = response.getBody();

            if (body == null || !body.containsKey("access_token")) {
                throw new BusinessException(AuthErrorCode.AUTH_401_INVALID_TOKEN);
            }

            return (String) body.get("access_token");

        } catch (HttpClientErrorException e) {
            // 유저가 구글 설정에서 앱 권한을 강제로 끊어버린 경우 (invalid_grant)
            log.error("구글 토큰 갱신 실패 (권한 취소 등): {}", e.getResponseBodyAsString());

            // TODO: 여기서 DB의 oauthRefreshToken을 null로 만들거나,
            // 프론트엔드에 "캘린더 연동이 끊어졌습니다. 다시 연동해주세요"라는 알림을 보내는 로직을 타야 합니다.
            throw new BusinessException(AuthErrorCode.AUTH_401_INVALID_TOKEN);

        } catch (Exception e) {
            log.error("구글 서버 통신 중 오류 발생", e);
            throw new BusinessException(AuthErrorCode.A105_AUTH_SESSION_400);
        }
    }
}