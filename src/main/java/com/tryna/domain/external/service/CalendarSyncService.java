package com.tryna.domain.external.service;

import com.tryna.domain.auth.entity.Auths;
import com.tryna.domain.auth.enums.Provider;
import com.tryna.domain.auth.repository.AuthsRepository;
import com.tryna.domain.auth.service.GoogleTokenProvider;
import com.tryna.domain.event.entity.Events;
import com.tryna.domain.event.entity.mapping.UserEvents;
import com.tryna.domain.event.repository.EventsRepository;
import com.tryna.domain.event.repository.UserEventsRepository;
import com.tryna.domain.external.dto.ExternalCalendarStatusResponse;
import com.tryna.domain.external.entity.ExternalCalendarConnections;
import com.tryna.domain.external.entity.ExternalCalendars;
import com.tryna.domain.external.repository.ExternalCalendarConnectionsRepository;
import com.tryna.domain.external.repository.ExternalCalendarsRepository;
import com.tryna.domain.user.entity.Users;
import com.tryna.domain.user.repository.UserRepository;
import com.tryna.global.exception.AuthErrorCode;
import com.tryna.global.exception.BusinessException;
import com.tryna.global.exception.ExternalEventErrorCode;
import com.tryna.global.exception.UserErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarSyncService {

    private final AuthsRepository authsRepository;
    private final GoogleTokenProvider googleTokenProvider;
    private final GoogleCalendarClient googleCalendarClient;

    private final UserRepository userRepository;
    private final EventsRepository eventsRepository;
    private final UserEventsRepository userEventsRepository;
    private final ExternalCalendarsRepository externalCalendarsRepository;
    private final ExternalCalendarConnectionsRepository externalCalendarConnectionsRepository;

    private final TransactionTemplate transactionTemplate;

    /**
     * B105: 외부 캘린더 일정 조회 및 표시 (대량 데이터 일괄 동기화 최적화 버전)
     */
    @Transactional
    public void syncGoogleCalendar(Long userId) {
        // 1. 유저 및 구글 연동 정보 검증
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_404));

        Auths auth = authsRepository.findByUser_UserIdAndProviderAndDeletedAtIsNull(userId, Provider.GOOGLE)
                .orElseThrow(() -> new BusinessException(ExternalEventErrorCode.B105_EXTERNAL_EVENT_400));

        // 2. 토큰 검증 및 재발급 (연동 레코드 생성보다 선행하여 유효하지 않은 계정의 부트스트랩 원천 차단)
        String refreshToken = auth.getOauthRefreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ExternalEventErrorCode.B105_EXTERNAL_EVENT_401);
        }

        String accessToken;
        try {
            accessToken = googleTokenProvider.getFreshAccessToken(refreshToken);
        } catch (BusinessException e) {
            boolean isTokenError = e.getErrorCode() == AuthErrorCode.AUTH_401_INVALID_TOKEN;
            if (isTokenError) {
                transactionTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                transactionTemplate.execute(status -> {
                    auth.clearOAuthInfo();
                    authsRepository.save(auth);
                    return null;
                });

                throw new BusinessException(ExternalEventErrorCode.B105_EXTERNAL_EVENT_401);
            } else {
                // 구글 API 서버 장애, 네트워크 타임아웃 등 서버/인프라 이슈는 500 에러로 분리 전파
                log.error("구글 토큰 갱신 중 외부 API 장애 또는 시스템 오류 발생: {}", e.getMessage(), e);
                throw new BusinessException(ExternalEventErrorCode.B105_EXTERNAL_EVENT_500);
            }
        }

        // 3. 토큰 검증이 성공한 이후에 안전하게 연동 정보 부트스트랩 수행 (동시성 충돌 방어 적용)
        ExternalCalendarConnections connection = externalCalendarConnectionsRepository
                .findByUser_UserIdAndProvider(userId, Provider.GOOGLE)
                .orElseGet(() -> {
                    try {
                        ExternalCalendarConnections newConn = ExternalCalendarConnections.create(
                                user, Provider.GOOGLE, auth.getOauthRefreshToken()
                        );
                        return externalCalendarConnectionsRepository.saveAndFlush(newConn);
                    } catch (org.springframework.dao.DataIntegrityViolationException e) {
                        // 동시 요청으로 인해 다른 트랜잭션이 먼저 커넥션을 생성한 경우 -> 기존 커넥션 조회 후 재사용
                        // 제약조건명 정밀 검사로 다른 무결성 오류 오인 방지
                        String rootMessage = e.getMostSpecificCause().getMessage();
                        if (rootMessage != null && rootMessage.contains("uq_external_calendar_connections_user_provider")) {
                            return externalCalendarConnectionsRepository
                                    .findByUser_UserIdAndProvider(userId, Provider.GOOGLE)
                                    .orElseThrow(() -> new BusinessException(ExternalEventErrorCode.B105_EXTERNAL_EVENT_500));
                        } else {
                            throw e;
                        }
                    }
                });

        ExternalCalendars externalCalendar = externalCalendarsRepository
                .findByConnection_User_UserIdAndConnection_Provider(userId, Provider.GOOGLE)
                .orElseGet(() -> {
                    try {
                        ExternalCalendars newCal = ExternalCalendars.createDefault(
                                connection, "primary", "내 캘린더"
                        );
                        return externalCalendarsRepository.saveAndFlush(newCal);
                    } catch (org.springframework.dao.DataIntegrityViolationException e) {
                        String rootMessage = e.getMostSpecificCause().getMessage();
                        // 캘린더 테이블의 유니크 제약조건명(uq_external_calendars_connection_calendar) 기준 검사
                        if (rootMessage != null && rootMessage.contains("uq_external_calendars_connection_calendar")) {
                            return externalCalendarsRepository
                                    .findByConnection_User_UserIdAndConnection_Provider(userId, Provider.GOOGLE)
                                    .orElseThrow(() -> new BusinessException(ExternalEventErrorCode.B105_EXTERNAL_EVENT_500));
                        } else {
                            throw e;
                        }
                    }
                });

        // 4. 구글 캘린더 조회 (한 달 치)
        ZonedDateTime timeMin = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        ZonedDateTime timeMax = timeMin.plusMonths(1);

        Map<String, Object> eventsData = googleCalendarClient.fetchEvents(accessToken, timeMin, timeMax);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) eventsData.get("items");

        if (items == null || items.isEmpty()) {
            log.info("구글 캘린더에 동기화할 일정이 없습니다.");
            return;
        }

        // 5. 성능 최적화: 구글 이벤트 ID 목록 추출 후 기존 일정 일괄 조회 (Batch Select)
        List<String> googleEventIds = items.stream()
                .map(item -> (String) item.get("id"))
                .toList();

        Map<String, Events> existingEventsMap = eventsRepository
                .findByExternalCalendarAndExternalEventIdIn(externalCalendar, googleEventIds)
                .stream()
                .collect(Collectors.toMap(Events::getExternalEventId, event -> event));

        List<Events> eventsToSave = new java.util.ArrayList<>();

        // 6. 구글 일정 파싱 및 메모리 내 분류 (업데이트 vs 신규 vs 삭제)
        for (Map<String, Object> item : items) {
            String googleEventId = (String) item.get("id");
            Events existingEvent = existingEventsMap.get(googleEventId);

            // 구글에서 삭제된 일정 처리
            if ("cancelled".equals(item.get("status"))) {
                if (existingEvent != null) {
                    existingEvent.deleteSoft(); // 더티 체킹으로 DB 반영됨
                }
                continue;
            }

            String summary = (String) item.get("summary");
            if (summary == null || summary.isBlank()) {
                summary = "제목 없음";
            }

            String description = (String) item.get("description");
            String location = (String) item.get("location");

            @SuppressWarnings("unchecked")
            Map<String, String> start = (Map<String, String>) item.get("start");
            @SuppressWarnings("unchecked")
            Map<String, String> end = (Map<String, String>) item.get("end");

            boolean isAllDay = start.containsKey("date");
            LocalDate startDate;
            LocalDateTime startDatetime = null;
            LocalDate endDate;
            LocalDateTime endDatetime = null;

            if (isAllDay) {
                startDate = LocalDate.parse(start.get("date"));
                endDate = LocalDate.parse(end.get("date")).minusDays(1);
            } else {
                startDatetime = ZonedDateTime.parse(start.get("dateTime"))
                        .withZoneSameInstant(ZoneId.of("Asia/Seoul"))
                        .toLocalDateTime();
                startDate = startDatetime.toLocalDate();

                endDatetime = ZonedDateTime.parse(end.get("dateTime"))
                        .withZoneSameInstant(ZoneId.of("Asia/Seoul"))
                        .toLocalDateTime();
                endDate = endDatetime.toLocalDate();
            }

            if (existingEvent != null) {
                // 더티 체킹을 통한 자동 업데이트 대상
                existingEvent.updateExternalEvent(summary, description, location, isAllDay, startDate, startDatetime, endDate, endDatetime);
            } else {
                // 신규 생성 대상
                Events newEvent = Events.createExternalEvent(
                        externalCalendar, googleEventId, summary, description, location, isAllDay, startDate, startDatetime, endDate, endDatetime
                );
                eventsToSave.add(newEvent);
            }
        }

        // 7. 신규 일정 일괄 저장 (Batch Insert) 및 UserEvents 매핑 일괄 생성
        if (!eventsToSave.isEmpty()) {
            eventsRepository.saveAll(eventsToSave);

            List<UserEvents> newUserEvents = eventsToSave.stream()
                    .map(newEvent -> UserEvents.createOwner(user, newEvent))
                    .toList();
            userEventsRepository.saveAll(newUserEvents);
        }

        // validItems 대신 items.size() 사용
        log.info("구글 캘린더 일정 대량 동기화 및 DB 적재 완료 (총 {}개 처리)", items.size());
    }

    /**
     * G102: 외부 캘린더 연동 상태 조회
     */
    @Transactional(readOnly = true)
    public ExternalCalendarStatusResponse getCalendarStatus(Long userId) {
        // 유저가 실제로 존재하는지 먼저 확인
        userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_404));

        Optional<ExternalCalendarConnections> connectionOpt = externalCalendarConnectionsRepository
                .findByUser_UserIdAndProvider(userId, Provider.GOOGLE);

        if (connectionOpt.isEmpty() || connectionOpt.get().getConnectionStatus() != com.tryna.domain.external.enums.ConnectionStatus.ACTIVE) {
            return new ExternalCalendarStatusResponse(false, null, null);
        }

        ExternalCalendars calendar = externalCalendarsRepository
                .findByConnection_User_UserIdAndConnection_Provider(userId, Provider.GOOGLE)
                .orElse(null);

        String calendarName = (calendar != null) ? calendar.getName() : "내 캘린더";

        return new ExternalCalendarStatusResponse(true, Provider.GOOGLE.name(), calendarName);
    }

    /**
     * G102: 외부 캘린더 연동 해제 (데이터 정리)
     */
    @Transactional
    public void disconnectGoogleCalendar(Long userId, Provider provider) {
        // 1. 유저가 실제로 존재하는지 먼저 확인
        userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_404));

        // 2. 해당 유저의 소셜 인증 정보(Auths)를 조회하여 리프레시 토큰 무효화 (재연동 우회 원천 차단)
        Auths auth = authsRepository.findByUser_UserIdAndProviderAndDeletedAtIsNull(userId, provider)
                .orElse(null);
        if (auth != null) {
            auth.clearOAuthInfo();
        }

        // 3. 해당 유저의 특정 프로바이더(외부 캘린더) 연동 정보 조회
        ExternalCalendarConnections connection = externalCalendarConnectionsRepository
                .findByUser_UserIdAndProvider(userId, provider)
                .orElse(null);

        if (connection != null) {
            // 커넥션 삭제 시 DB CASCADE 체인에 의해
            // ExternalCalendars -> Events -> UserEvents 순으로 연관된 외부 일정 데이터들이 모두 안전하게 함께 삭제됨.
            externalCalendarConnectionsRepository.delete(connection);
        }

        log.info("유저 ID {}의 {} 캘린더 연동 정보 및 관련 일정 데이터가 완전히 삭제되었습니다.", userId, provider);
    }
}