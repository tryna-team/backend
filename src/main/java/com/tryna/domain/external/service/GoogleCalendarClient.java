package com.tryna.domain.external.service;

import com.tryna.global.exception.BusinessException;
import com.tryna.global.exception.ExternalEventErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.ZonedDateTime;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleCalendarClient {

    private final RestTemplate restTemplate;

    @Value("${oauth2.google.calendar-url}")
    private String calendarApiUrl;

    public Map<String, Object> fetchEvents(String accessToken, ZonedDateTime timeMin, ZonedDateTime timeMax) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // 1. +09:00 대신, UTC(Z) 포맷으로 변환 (2026-07-31T21:41:16Z)
        String formattedTimeMin = timeMin.toInstant().toString();
        String formattedTimeMax = timeMax.toInstant().toString();

        // 2. URL을 직접 짜깁기하지 않고, 스프링에 변수({timeMin})로 넘겨서 인코딩 처리
        String uriTemplate = calendarApiUrl + "?timeMin={timeMin}&timeMax={timeMax}&singleEvents=true&orderBy=startTime";

        try {
            // 스프링 RestTemplate이 uriTemplate의 {} 괄호 안에 순서대로 값을 넣으며 가장 안전하게 인코딩
            ResponseEntity<Map> response = restTemplate.exchange(
                    uriTemplate,
                    HttpMethod.GET,
                    request,
                    Map.class,
                    formattedTimeMin, // 첫 번째 {timeMin} 에 들어갈 값
                    formattedTimeMax  // 두 번째 {timeMax} 에 들어갈 값
            );
            return response.getBody();
        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized
                 | org.springframework.web.client.HttpClientErrorException.Forbidden e) {
            // 구글 캘린더 API 인증/권한 거부(401/403)는 500이 아닌 B105_EXTERNAL_EVENT_401로 정확히 분리 전파
            log.warn("구글 캘린더 API 인증 또는 권한 오류 발생 (401/403): {}", e.getMessage());
            throw new BusinessException(ExternalEventErrorCode.B105_EXTERNAL_EVENT_401);
        } catch (Exception e) {
            // 그 외 네트워크 타임아웃, 구글 서버 장애 등은 500 에러 처리
            log.error("구글 캘린더 API 통신 실패: {}", e.getMessage(), e);
            throw new BusinessException(ExternalEventErrorCode.B105_EXTERNAL_EVENT_500);
        }
    }
}