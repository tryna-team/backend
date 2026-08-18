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

    // 기존 4개 파라미터 호출을 위한 편의 메서드 (호환성 유지)
    public Map<String, Object> fetchEvents(String accessToken, LocalDateTime lastSyncedAt, ZonedDateTime timeMin, ZonedDateTime timeMax) {
        return fetchEvents(accessToken, lastSyncedAt, timeMin, timeMax, null);
    }

    // 5개 파라미터 (pageToken 지원)를 받는 핵심 메서드
    public Map<String, Object> fetchEvents(String accessToken, LocalDateTime lastSyncedAt, ZonedDateTime timeMin, ZonedDateTime timeMax, String pageToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        String uriTemplate = calendarApiUrl + "?singleEvents=true&showDeleted=true";
        List<Object> uriVariables = new java.util.ArrayList<>();

        if (lastSyncedAt != null) {
            // [핵심 해결 포인트] 증분 동기화 시 timeMin/timeMax 필터를 제외해야 구글이 삭제된(cancelled) 일정을 보내줍니다.
            String updatedMin = lastSyncedAt.atZone(ZoneId.of("Asia/Seoul")).toInstant().toString();
            uriTemplate += "&updatedMin={updatedMin}";
            uriVariables.add(updatedMin);
        } else {
            // 최초 Full 동기화 시에는 해당 연도 데이터만 가져오도록 범위 지정
            uriTemplate += "&timeMin={timeMin}&timeMax={timeMax}&orderBy=startTime";
            uriVariables.add(timeMin.toInstant().toString());
            uriVariables.add(timeMax.toInstant().toString());
        }

        // 페이지네이션 토큰이 존재하면 쿼리 파라미터 추가
        if (pageToken != null && !pageToken.isBlank()) {
            uriTemplate += "&pageToken={pageToken}";
            uriVariables.add(pageToken);
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