package com.tryna.domain.external.service;

import com.tryna.domain.auth.entity.Auths;
import com.tryna.domain.auth.enums.Provider;
import com.tryna.domain.auth.repository.AuthsRepository;
import com.tryna.domain.auth.service.GoogleTokenProvider;
import com.tryna.domain.event.entity.Events;
import com.tryna.domain.event.entity.mapping.UserEvents;
import com.tryna.domain.event.repository.EventsRepository;
import com.tryna.domain.event.repository.UserEventsRepository;
import com.tryna.domain.external.dto.CalendarStatusResponse;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Locale;
import java.util.stream.Collectors;

import com.tryna.domain.label.enums.LabelColor;
import com.tryna.domain.label.service.DefaultLabelService;
import com.tryna.domain.label.entity.Labels;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarSyncService {

    private static final int MAX_SYNC_ITEMS_LIMIT = 1000;

    private final AuthsRepository authsRepository;
    private final GoogleTokenProvider googleTokenProvider;
    private final GoogleCalendarClient googleCalendarClient;

    private final UserRepository userRepository;
    private final EventsRepository eventsRepository;
    private final UserEventsRepository userEventsRepository;
    private final ExternalCalendarsRepository externalCalendarsRepository;
    private final ExternalCalendarConnectionsRepository externalCalendarConnectionsRepository;
    private final com.tryna.domain.label.repository.LabelsRepository labelsRepository;
    private final DefaultLabelService defaultLabelService;

    private final PlatformTransactionManager transactionManager;

    /**
     * B105: 외부 캘린더 일정 조회 및 표시 (연도 단위 동기화)
     * @param userId 유저 ID
     * @param targetYear 동기화할 연도 (null인 경우 기본값: 현재 연도)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncGoogleCalendar(Long userId, Integer targetYear) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        // 1. 유저 및 구글 연동 정보 검증
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_404));

        Auths auth = authsRepository.findByUser_UserIdAndProviderAndDeletedAtIsNull(userId, Provider.GOOGLE)
                .orElseThrow(() -> new BusinessException(ExternalEventErrorCode.B105_EXTERNAL_EVENT_400));

        // 2. 토큰 검증 및 재발급
        String refreshToken = auth.getOauthRefreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            markSyncFailed(userId);
            throw new BusinessException(ExternalEventErrorCode.B105_EXTERNAL_EVENT_401);
        }

        String accessToken;
        // 기록: 동기화 시작 시 IN_PROGRESS로 상태를 남겨 프론트에서 진행 상태를 표시할 수 있도록 함
        try {
            TransactionTemplate requiresNewTemplate = new TransactionTemplate(transactionManager);
            requiresNewTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

            requiresNewTemplate.execute(status -> {
                externalCalendarConnectionsRepository.findByUser_UserIdAndProvider(userId, Provider.GOOGLE)
                        .ifPresent(conn -> {
                            conn.updateSyncStatus(LocalDateTime.now(), "IN_PROGRESS");
                            externalCalendarConnectionsRepository.saveAndFlush(conn);
                        });
                return null;
            });
        } catch (Exception ex) {
            log.warn("유저 {} 구글 동기화 IN_PROGRESS 상태 기록 중 오류 발생: {}", userId, ex.getMessage());
        }

        try {
            accessToken = googleTokenProvider.getFreshAccessToken(refreshToken);
        } catch (BusinessException e) {
            boolean isTokenError = e.getErrorCode() == AuthErrorCode.AUTH_401_INVALID_TOKEN;
            if (isTokenError) {
                TransactionTemplate requiresNewTemplate = new TransactionTemplate(transactionManager);
                requiresNewTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

                requiresNewTemplate.execute(status -> {
                    auth.clearOAuthInfo();
                    authsRepository.save(auth);
                    return null;
                });

                markSyncFailed(userId);
                throw new BusinessException(ExternalEventErrorCode.B105_EXTERNAL_EVENT_401);
            } else {
                log.error("구글 토큰 갱신 중 외부 API 장애 또는 시스템 오류 발생: {}", e.getMessage(), e);
                markSyncFailed(userId);
                throw new BusinessException(ExternalEventErrorCode.B105_EXTERNAL_EVENT_500);
            }
        }

        // 3. 연동 정보 부트스트랩 수행 (동시성 충돌 방어 적용) - DB 트랜잭션 범위 1
        ExternalCalendars externalCalendar;
        try {
            externalCalendar = transactionTemplate.execute(status -> {
                ExternalCalendarConnections connection = externalCalendarConnectionsRepository
                        .findByUser_UserIdAndProvider(userId, Provider.GOOGLE)
                        .orElseGet(() -> {
                            try {
                                ExternalCalendarConnections newConn = ExternalCalendarConnections.create(
                                        user, Provider.GOOGLE, auth.getOauthRefreshToken()
                                );
                                return externalCalendarConnectionsRepository.saveAndFlush(newConn);
                            } catch (org.springframework.dao.DataIntegrityViolationException e) {
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

                ExternalCalendars externalCal = externalCalendarsRepository
                        .findByConnection_User_UserIdAndConnection_ProviderAndProviderExternalCalendarId(userId, Provider.GOOGLE, "primary")
                        .orElseGet(() -> {
                            try {
                                ExternalCalendars newCal = ExternalCalendars.createDefault(
                                        connection, "primary", "Google 캘린더"
                                );
                                return externalCalendarsRepository.saveAndFlush(newCal);
                            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                                String rootMessage = e.getMostSpecificCause().getMessage();
                                if (rootMessage != null && rootMessage.contains("uq_external_calendars_connection_calendar")) {
                                    return externalCalendarsRepository
                                            .findByConnection_User_UserIdAndConnection_ProviderAndProviderExternalCalendarId(userId, Provider.GOOGLE, "primary")
                                            .orElseThrow(() -> new BusinessException(ExternalEventErrorCode.B105_EXTERNAL_EVENT_500));
                                } else {
                                    throw e;
                                }
                            }
                        });

                labelsRepository.findByExternalCalendar(externalCal).orElseGet(() -> {
                    try {
                        Integer nextSort = labelsRepository
                                .findTopByUser_UserIdOrderBySortOrderDesc(userId)
                                .map(label -> label.getSortOrder() + 1)
                                .orElse(1);

                        String calName = externalCal.getName() != null ? externalCal.getName() : "외부 캘린더";
                        String normalized = calName.trim().toLowerCase(Locale.ROOT);

                        com.tryna.domain.label.entity.Labels newLabel = com.tryna.domain.label.entity.Labels.createExternalCalendarLabel(
                                user,
                                externalCal,
                                calName,
                                normalized,
                                LabelColor.BLUE,
                                nextSort
                        );

                        return labelsRepository.saveAndFlush(newLabel);
                    } catch (org.springframework.dao.DataIntegrityViolationException e) {
                        String rootMessage = e.getMostSpecificCause().getMessage();
                        if (rootMessage != null && rootMessage.contains("uq_labels_external_calendar_active")) {
                            return labelsRepository.findByExternalCalendar(externalCal)
                                    .orElseThrow(() -> new BusinessException(ExternalEventErrorCode.B105_EXTERNAL_EVENT_500));
                        } else {
                            throw e;
                        }
                    }
                });

                return externalCal;
            });
        } catch (Exception e) {
            markSyncFailed(userId);
            throw e;
        }

        Long connectionId = (externalCalendar != null && externalCalendar.getConnection() != null) ?
                externalCalendar.getConnection().getExternalCalendarConnectionId() : null;

        com.tryna.domain.label.entity.Labels externalLabel = labelsRepository
                .findByExternalCalendar(externalCalendar)
                .orElseThrow(() -> {
                    markSyncFailed(userId);
                    return new BusinessException(ExternalEventErrorCode.B105_EXTERNAL_EVENT_500);
                });

        // 4. 동기화 범위 결정 (연도 단위: 1월 1일 ~ 12월 31일)
        ZoneId seoulZone = ZoneId.of("Asia/Seoul");
        int syncYear = (targetYear != null) ? targetYear : ZonedDateTime.now(seoulZone).getYear();

        if (syncYear < 2000 || syncYear > 2100) {
            log.warn("외부 캘린더 동기화 요청 연도 범위 오류 - year: {}", syncYear);
            markSyncFailed(userId);
            throw new BusinessException(ExternalEventErrorCode.B105_EXTERNAL_EVENT_400);
        }

        ZonedDateTime timeMin = ZonedDateTime.of(syncYear, 1, 1, 0, 0, 0, 0, seoulZone);
        ZonedDateTime timeMax = ZonedDateTime.of(syncYear, 12, 31, 23, 59, 59, 999999999, seoulZone);

        // 5. 구글 API 통신 및 DB 업데이트
        try {
            Map<String, Object> eventsData = googleCalendarClient.fetchEvents(accessToken, timeMin, timeMax);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) eventsData.get("items");

            if (items == null || items.isEmpty()) {
                log.info("구글 캘린더에 동기화할 일정이 없습니다. (조회 연도: {}년)", syncYear);
                if (connectionId != null) {
                    externalCalendarConnectionsRepository.findById(connectionId).ifPresent(conn -> {
                        conn.updateSyncStatus(LocalDateTime.now(), "SUCCESS");
                        externalCalendarConnectionsRepository.save(conn);
                    });
                }
                return;
            }

            if (items.size() > MAX_SYNC_ITEMS_LIMIT) {
                log.warn("외부 캘린더 일정이 허용 수량을 초과했습니다. (요청 수: {}개, 최대 허용: {}개)", items.size(), MAX_SYNC_ITEMS_LIMIT);
                throw new BusinessException(ExternalEventErrorCode.B105_EXTERNAL_EVENT_400);
            }

            transactionTemplate.executeWithoutResult(status -> {
                List<String> googleEventIds = items.stream()
                        .map(item -> (String) item.get("id"))
                        .toList();

                Map<String, Events> existingEventsMap = eventsRepository
                        .findByExternalCalendarAndExternalEventIdIn(externalCalendar, googleEventIds)
                        .stream()
                        .collect(Collectors.toMap(Events::getExternalEventId, event -> event));

                List<Events> eventsToSave = new java.util.ArrayList<>();

                for (Map<String, Object> item : items) {
                    String googleEventId = (String) item.get("id");
                    Events existingEvent = existingEventsMap.get(googleEventId);

                    if ("cancelled".equals(item.get("status"))) {
                        if (existingEvent != null) {
                            existingEvent.deleteSoft();
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
                        existingEvent.updateExternalEvent(summary, description, location, isAllDay, startDate, startDatetime, endDate, endDatetime);
                    } else {
                        Events newEvent = Events.createExternalEvent(
                                externalCalendar, googleEventId, summary, description, location, isAllDay, startDate, startDatetime, endDate, endDatetime
                        );
                        eventsToSave.add(newEvent);
                    }
                }

                if (!eventsToSave.isEmpty()) {
                    eventsRepository.saveAll(eventsToSave);

                    List<UserEvents> newUserEvents = eventsToSave.stream()
                            .map(newEvent -> UserEvents.createOwner(user, newEvent, externalLabel))
                            .toList();
                    userEventsRepository.saveAll(newUserEvents);
                }
            });

            log.info("구글 캘린더 일정 동기화 완료 (조회 범위: {} ~ {}, 총 {}개 처리)", timeMin, timeMax, items.size());

            if (connectionId != null) {
                externalCalendarConnectionsRepository.findById(connectionId).ifPresent(conn -> {
                    conn.updateSyncStatus(LocalDateTime.now(), "SUCCESS");
                    externalCalendarConnectionsRepository.save(conn);
                });
            }
        } catch (Exception e) {
            markSyncFailed(userId);
            throw e;
        }
    }

    /**
     * 동기화 실패 상태를 독립 트랜잭션(REQUIRES_NEW)으로 안전하게 DB에 즉시 저장합니다.
     */
    private void markSyncFailed(Long userId) {
        try {
            TransactionTemplate requiresNewTemplate = new TransactionTemplate(transactionManager);
            requiresNewTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

            requiresNewTemplate.executeWithoutResult(status -> {
                externalCalendarConnectionsRepository.findByUser_UserIdAndProvider(userId, Provider.GOOGLE)
                        .ifPresent(conn -> {
                            conn.updateSyncStatus(LocalDateTime.now(), "FAILED");
                            externalCalendarConnectionsRepository.saveAndFlush(conn);
                        });
            });
        } catch (Exception ex) {
            log.warn("유저 {} 구글 동기화 FAILED 상태 기록 중 오류 발생: {}", userId, ex.getMessage());
        }
    }

    /**
     * G102: 외부 캘린더 연동 상태 조회 (통합 반환)
     */
    @Transactional(readOnly = true)
    public CalendarStatusResponse getUnifiedCalendarStatus(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_404));

        Optional<ExternalCalendarConnections> connectionOpt = externalCalendarConnectionsRepository
                .findByUser_UserIdAndProvider(userId, Provider.GOOGLE);

        if (connectionOpt.isEmpty() || connectionOpt.get().getConnectionStatus() != com.tryna.domain.external.enums.ConnectionStatus.ACTIVE) {
            return new CalendarStatusResponse(false, "NONE", null, "No external calendar linked");
        }

        ExternalCalendarConnections conn = connectionOpt.get();
        String syncStatus = conn.getLastSyncStatus() == null ? "NONE" : conn.getLastSyncStatus();

        return new CalendarStatusResponse(true, syncStatus, conn.getLastSyncedAt(), null);
    }

    /**
     * G102: 외부 캘린더 연동 해제 (데이터 정리)
     */
    @Transactional
    public void disconnectGoogleCalendar(Long userId, Provider provider) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_404));

        Auths auth = authsRepository.findByUser_UserIdAndProviderAndDeletedAtIsNull(userId, provider)
                .orElse(null);
        if (auth != null) {
            auth.clearOAuthInfo();
        }

        Labels defaultLabel = labelsRepository.findByUser_UserIdAndIsDefaultTrue(userId)
                .orElseGet(() -> {
                    defaultLabelService.createDefaultLabel(user);
                    return labelsRepository.findByUser_UserIdAndIsDefaultTrue(userId)
                            .orElseThrow(() -> new BusinessException(UserErrorCode.USER_404));
                });

        ExternalCalendarConnections connection = externalCalendarConnectionsRepository
                .findByUser_UserIdAndProvider(userId, provider)
                .orElse(null);

        if (connection != null) {
            List<ExternalCalendars> calendars = externalCalendarsRepository.findAllByConnection(connection);

            for (ExternalCalendars cal : calendars) {
                labelsRepository.findByExternalCalendar(cal).ifPresent(extLabel -> {
                    Long sourceLabelId = extLabel.getLabelId();
                    userEventsRepository.moveLabelAssignments(userId, sourceLabelId, defaultLabel);
                });

                int deleted = labelsRepository.deleteByExternalCalendar(cal);
                if (deleted > 0) {
                    labelsRepository.flush();
                }
            }

            externalCalendarsRepository.deleteAll(calendars);
            externalCalendarConnectionsRepository.delete(connection);
        }

        log.info("유저 ID {}의 {} 캘린더 연동 정보 및 관련 일정/라벨 데이터 정리가 완료되었습니다.", userId, provider);
    }
}