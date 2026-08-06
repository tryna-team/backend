package com.tryna.domain.auth.service;

import com.tryna.global.exception.AuthErrorCode;
import com.tryna.global.exception.BusinessException;
import com.tryna.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleTokenProvider {

    private final RestTemplate restTemplate;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${oauth2.google.client-id}")
    private String googleClientId;

    @Value("${oauth2.google.client-secret}")
    private String googleClientSecret;

    @Value("${oauth2.google.token-url:https://oauth2.googleapis.com/token}")
    private String googleTokenUrl;

    /**
     * DB에 저장된 구글 Refresh Token을 사용해 새로운 Access Token을 발급받습니다.
     */
    public String getFreshAccessToken(String googleRefreshToken) {
        if (googleRefreshToken == null || googleRefreshToken.isBlank()) {
            throw new BusinessException(AuthErrorCode.AUTH_401_INVALID_TOKEN);
        }

        HttpHeaders headers = new HttpHeaders();
        // 구글 요구 form-urlencoded 방식 설정
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        int maxAttempts = 3;
        int attempt = 0;
        long backoffMs = 500;

        while (true) {
            attempt++;

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("client_id", googleClientId);
            params.add("client_secret", googleClientSecret);
            params.add("refresh_token", googleRefreshToken);
            params.add("grant_type", "refresh_token");

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            try {
                ResponseEntity<Map> response = restTemplate.postForEntity(googleTokenUrl, request, Map.class);
                Map<String, Object> body = response.getBody();

                if (body == null) {
                    log.warn("구글 토큰 응답 본문(body)이 비어 있음 (will retry {} / {})", attempt, maxAttempts);
                    if (attempt >= maxAttempts) {
                        log.error("구글 토큰 응답 본문 누락 (최대 재시도 도달)");
                        throw new BusinessException(CommonErrorCode.COMMON_500);
                    }
                    try { Thread.sleep(backoffMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw new BusinessException(CommonErrorCode.COMMON_500); }
                    backoffMs *= 2;
                    continue;
                }

                // 꺼낸 값이 String 타입이 맞는지 확인과 동시에 accessToken 변수에 담고, 빈 값인지까지 방어
                Object tokenObj = body.get("access_token");
                if (!(tokenObj instanceof String accessToken) || accessToken.isBlank()) {
                    log.warn("구글 토큰 응답에 access_token이 누락되었거나 형식이 올바르지 않음 (will retry {} / {})", attempt, maxAttempts);
                    if (attempt >= maxAttempts) {
                        log.error("구글 토큰 응답 access_token 누락 (최대 재시도 도달)");
                        throw new BusinessException(CommonErrorCode.COMMON_500);
                    }
                    try { Thread.sleep(backoffMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw new BusinessException(CommonErrorCode.COMMON_500); }
                    backoffMs *= 2;
                    continue;
                }

                return accessToken;

            } catch (HttpClientErrorException e) {
                try {
                    // 구글이 보낸 에러 응답(JSON)을 파싱
                    JsonNode errorNode = objectMapper.readTree(e.getResponseBodyAsString());
                    String googleError = errorNode.path("error").asText();

                    if ("invalid_grant".equals(googleError)) {
                        // 유저의 토큰이 만료/취소된 경우 (401 에러) - 재시도 불가
                        log.warn("구글 리프레시 토큰 만료/취소됨 (invalid_grant): {}", e.getResponseBodyAsString());
                        throw new BusinessException(AuthErrorCode.AUTH_401_INVALID_TOKEN);
                    } else {
                        // transient or server-side error - 로그 기록 후 재시도 가능
                        log.warn("구글 토큰 교환 중 예상치 못한 에러 (will retry {} / {}): {}", attempt, maxAttempts, e.getResponseBodyAsString());
                        if (attempt >= maxAttempts) {
                            log.error("구글 서버 연동/설정 오류 (최대 재시도 도달): {}", e.getResponseBodyAsString());
                            throw new BusinessException(CommonErrorCode.COMMON_500);
                        }
                        try { Thread.sleep(backoffMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw new BusinessException(CommonErrorCode.COMMON_500); }
                        backoffMs *= 2;
                        continue;
                    }
                } catch (BusinessException be) {
                    throw be;
                } catch (Exception parseEx) {
                    // JSON 파싱 실패: 재시도 가능
                    log.warn("구글 에러 응답 JSON 파싱 실패 (will retry {} / {}): {}", attempt, maxAttempts, parseEx.getMessage());
                    if (attempt >= maxAttempts) {
                        log.error("구글 에러 응답 JSON 파싱 실패(최대 재시도 도달)", parseEx);
                        throw new BusinessException(CommonErrorCode.COMMON_500);
                    }
                    try { Thread.sleep(backoffMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw new BusinessException(CommonErrorCode.COMMON_500); }
                    backoffMs *= 2;
                    continue;
                }

            } catch (BusinessException e) {
                // 의도적으로 던진 401 예외는 그대로 통과
                throw e;

            } catch (Exception e) {
                log.warn("구글 서버 통신 중 알 수 없는 오류 발생 (will retry {} / {}): {}", attempt, maxAttempts, e.getMessage());
                if (attempt >= maxAttempts) {
                    log.error("구글 서버 통신 중 알 수 없는 오류 발생(최대 재시도 도달)", e);
                    throw new BusinessException(CommonErrorCode.COMMON_500);
                }
                try { Thread.sleep(backoffMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw new BusinessException(CommonErrorCode.COMMON_500); }
                backoffMs *= 2;
                continue;
            }
        }
    }
}