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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleCalendarClient {

    private final RestTemplate restTemplate;

    @Value("${oauth2.google.calendar-url}")
    private String calendarApiUrl;

    public Map<String, Object> fetchEvents(String accessToken, LocalDateTime lastSyncedAt, ZonedDateTime timeMin, ZonedDateTime timeMax) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        String uriTemplate = calendarApiUrl + "?timeMin={timeMin}&timeMax={timeMax}&singleEvents=true&showDeleted=true";
        List<Object> uriVariables = new java.util.ArrayList<>();
        uriVariables.add(timeMin.toInstant().toString());
        uriVariables.add(timeMax.toInstant().toString());

        if (lastSyncedAt != null) {
            // 마지막 동기화 시간 이후에 변경된 데이터만 요청 (updatedMin)
            String updatedMin = lastSyncedAt.atZone(ZoneId.of("Asia/Seoul")).toInstant().toString();
            uriTemplate += "&updatedMin={updatedMin}";
            uriVariables.add(updatedMin);
        } else {
            // 최초 동기화
            uriTemplate += "&orderBy=startTime";
        }

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    uriTemplate, HttpMethod.GET, request, Map.class, uriVariables.toArray()
            );
            return response.getBody();

        } catch (org.springframework.web.client.HttpClientErrorException.Gone e) {
            // 410 Gone: 동기화 기준 시간(updatedMin)이 구글 정책상 너무 오래되어 파기된 경우
            log.warn("구글 캘린더 updatedMin 기준 시간 만료 (410 Gone). 전체 동기화가 필요합니다.");
            throw new BusinessException(ExternalEventErrorCode.B105_EXTERNAL_EVENT_410);

        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized
                 | org.springframework.web.client.HttpClientErrorException.Forbidden e) {
            log.warn("구글 캘린더 API 인증 또는 권한 오류 발생 (401/403): {}", e.getMessage());
            throw new BusinessException(ExternalEventErrorCode.B105_EXTERNAL_EVENT_401);
        } catch (Exception e) {
            log.error("구글 캘린더 API 통신 실패: {}", e.getMessage(), e);
            throw new BusinessException(ExternalEventErrorCode.B105_EXTERNAL_EVENT_500);
        }
    }
}