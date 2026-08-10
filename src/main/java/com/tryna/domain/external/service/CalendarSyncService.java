package com.tryna.domain.external.service;

import com.tryna.domain.auth.entity.Auths;
import com.tryna.domain.auth.enums.Provider;
import com.tryna.domain.auth.repository.AuthsRepository;
import com.tryna.domain.auth.service.GoogleTokenProvider;
import com.tryna.domain.event.entity.Events;
import com.tryna.domain.event.entity.mapping.UserEvents;
import com.tryna.domain.event.enums.EventStatus;
import com.tryna.domain.event.repository.EventsRepository;
import com.tryna.domain.event.repository.UserEventsRepository;
import com.tryna.domain.external.dto.CalendarStatusResponse;
import com.tryna.domain.external.entity.ExternalCalendarConnections;
import com.tryna.domain.external.entity.ExternalCalendars;
import com.tryna.domain.external.repository.ExternalCalendarConnectionsRepository;
import com.tryna.domain.external.repository.ExternalCalendarsRepository;
import com.tryna.domain.reminder.repository.RemindersRepository;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
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
    private final RemindersRepository remindersRepository;
    private final DefaultLabelService defaultLabelService;

    private final PlatformTransactionManager transactionManager;

    /**
     * B105: 외부 캘린더 일정 조회 및 표시 (연도 단위 동기화)
     *
     * @param userId     유저 ID
     * @param targetYear 동기화할 연도 (null인 경우 기본값: 현재 연도)
     */
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
                            // IN_PROGRESS 상태 기록 시, 기존의 성공 시간(lastSyncedAt)을 날리지 않고 그대로 유지
                            conn.updateSyncStatus(conn.getLastSyncedAt(), "IN_PROGRESS");
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

        // 3. 연동 정보 부트스트랩 수행 (동시성 충돌 방어 적용)
        TransactionTemplate requiresNewTemplate = new TransactionTemplate(transactionManager);
        requiresNewTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        ExternalCalendars externalCalendar;
        Long connectionId;
        ExternalCalendarConnections connection;

        try {
            connection = externalCalendarConnectionsRepository
                    .findByUser_UserIdAndProvider(userId, Provider.GOOGLE)
                    .orElseGet(() -> {
                        try {
                            return requiresNewTemplate.execute(status -> {
                                ExternalCalendarConnections newConn = ExternalCalendarConnections.create(
                                        user, Provider.GOOGLE, auth.getOauthRefreshToken()
                                );
                                return externalCalendarConnectionsRepository.saveAndFlush(newConn);
                            });
                        } catch (org.springframework.dao.DataIntegrityViolationException e) {
                            return externalCalendarConnectionsRepository
                                    .findByUser_UserIdAndProvider(userId, Provider.GOOGLE)
                                    .orElseThrow(() -> new BusinessException(ExternalEventErrorCode.B105_EXTERNAL_EVENT_500));
                        }
                    });

            // LazyInitializationException 방지를 위해 안전한 스코프에서 미리 ID를 뽑아둡니다.
            connectionId = connection.getExternalCalendarConnectionId();

            externalCalendar = externalCalendarsRepository
                    .findByConnection_User_UserIdAndConnection_ProviderAndProviderExternalCalendarId(userId, Provider.GOOGLE, "primary")
                    .orElseGet(() -> {
                        try {
                            return requiresNewTemplate.execute(status -> {
                                ExternalCalendars newCal = ExternalCalendars.createDefault(
                                        connection, "primary", "Google 캘린더"
                                );
                                return externalCalendarsRepository.saveAndFlush(newCal);
                            });
                        } catch (org.springframework.dao.DataIntegrityViolationException e) {
                            return externalCalendarsRepository
                                    .findByConnection_User_UserIdAndConnection_ProviderAndProviderExternalCalendarId(userId, Provider.GOOGLE, "primary")
                                    .orElseThrow(() -> new BusinessException(ExternalEventErrorCode.B105_EXTERNAL_EVENT_500));
                        }
                    });

            com.tryna.domain.label.entity.Labels externalLabel = getOrCreateExternalLabel(requiresNewTemplate, user, externalCalendar, userId);

        } catch (Exception e) {
            markSyncFailed(userId);
            throw e;
        }

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
            // 1. 마지막으로 동기화했던 시간을 꺼냄
            LocalDateTime currentLastSyncedAt = connection.getLastSyncedAt();

            // 2. 지금 조회하려는 연도(예: 2025년)에 우리 DB 일정이 하나라도 있나?
            boolean hasEventsForThisYear = hasEventsInYear(externalCalendar, syncYear);

            // 3. 하나도 없다면? (처음 2025년 달력을 본 것)
            if (!hasEventsForThisYear) {
                currentLastSyncedAt = null;     // 마지막 동기화 시간 무시하고 전체를 가져옴
                log.info("해당 연도({})에 저장된 일정이 없어 Full Sync로 전환합니다.", syncYear);
            }

            Map<String, Object> eventsData;

            // 1. 구글 요청 직전의 시점을 Asia/Seoul 기준으로 정확히 캡처 (Request-start watermark)
            LocalDateTime initialWatermark = ZonedDateTime.now(seoulZone).toLocalDateTime();
            LocalDateTime fallbackWatermark = null;
            boolean isFallback = false;

            try {
                eventsData = googleCalendarClient.fetchEvents(accessToken, currentLastSyncedAt, timeMin, timeMax);
            } catch (BusinessException e) {
                // 410 Gone (기준 시간 만료) 방어: Full Sync로 전환 (기존 성공 커서 보존)
                if (e.getErrorCode() == ExternalEventErrorCode.B105_EXTERNAL_EVENT_410) {
                    log.info("동기화 기준 시간이 너무 오래되어 Full Sync로 재시도합니다. userId: {}", userId);

                    transactionTemplate.executeWithoutResult(status -> {
                        // 기존 성공 커서를 날리지 않고 유지만 한 채 IN_PROGRESS 상태 갱신
                        connection.updateSyncStatus(connection.getLastSyncedAt(), "IN_PROGRESS");
                        externalCalendarConnectionsRepository.saveAndFlush(connection);
                    });

                    // Full Sync 시점의 새로운 요청 시작 커서 캡처
                    fallbackWatermark = ZonedDateTime.now(seoulZone).toLocalDateTime();
                    isFallback = true;
                    eventsData = googleCalendarClient.fetchEvents(accessToken, null, timeMin, timeMax);
                } else {
                    throw e;
                }
            }

            // 람다식 안에서 안전하게 쓸 수 있도록 effectively final(최종 단일 할당) 변수로 확정
            final LocalDateTime requestStartWatermark = isFallback ? fallbackWatermark : initialWatermark;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) eventsData.get("items");

            if (items == null || items.isEmpty()) {
                log.info("구글 캘린더에 동기화할 일정(변경분)이 없습니다. (조회 연도: {}년)", syncYear);
            } else {
                if (items.size() > MAX_SYNC_ITEMS_LIMIT) {
                    log.warn("외부 캘린더 일정이 허용 수량을 초과했습니다. (요청 수: {}개, 최대 허용: {}개)", items.size(), MAX_SYNC_ITEMS_LIMIT);
                    throw new BusinessException(ExternalEventErrorCode.B105_EXTERNAL_EVENT_400);
                }

                transactionTemplate.executeWithoutResult(status -> {
                    List<String> googleEventIds = items.stream()
                            .map(item -> (String) item.get("id"))
                            .toList();

                    // Batch Select 실행
                    List<Events> existingEvents = eventsRepository.findAllIncludingDeletedByExternalCalendarIdAndExternalEventIdIn(
                            externalCalendar.getExternalCalendarId(),
                            googleEventIds
                    );

                    // 중복 키 에러 방어 로직 추가
                    Map<String, Events> existingEventsMap = existingEvents.stream()
                            .collect(Collectors.toMap(
                                    Events::getExternalEventId,
                                    event -> event,
                                    (existing, replacement) -> existing
                            ));

                    List<Events> eventsToSave = new java.util.ArrayList<>();

                    for (Map<String, Object> item : items) {
                        String googleEventId = (String) item.get("id");
                        Events existingEvent = existingEventsMap.get(googleEventId);

                        if ("cancelled".equals(item.get("status"))) {
                            if (existingEvent != null) {
                                // 1. 아직 Soft Delete 되지 않았다면 처리
                                if (existingEvent.getDeletedAt() == null) {
                                    existingEvent.deleteSoft();
                                    eventsRepository.saveAndFlush(existingEvent);
                                }

                                // 2. 기존 이벤트 존재 여부와 무관하게 연관된 리마인드 물리 삭제 (Hard Delete) 보장
                                remindersRepository.deleteByEventIdCascade(existingEvent.getEventId());
                                log.info("구글 캘린더에서 삭제된 일정 감지 및 리마인드 정리 완료: {}", googleEventId);
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
                                    .withZoneSameInstant(seoulZone)
                                    .toLocalDateTime();
                            startDate = startDatetime.toLocalDate();

                            endDatetime = ZonedDateTime.parse(end.get("dateTime"))
                                    .withZoneSameInstant(seoulZone)
                                    .toLocalDateTime();
                            endDate = endDatetime.toLocalDate();
                        }

                        if (existingEvent != null) {
                            // 기존 이벤트가 존재할 경우
                            if (existingEvent.getDeletedAt() != null) {
                                // 연동 해제로 잠들어 있던 일정 -> 새로운 externalCalendar와 함께 되살리기(Resurrect)
                                existingEvent.resurrectExternalEvent(externalCalendar, summary, description, location, isAllDay, startDate, startDatetime, endDate, endDatetime);
                                eventsToSave.add(existingEvent);
                            } else {
                                // 케이스 B: 평소처럼 살아있는 기존 일정 -> 내용만 최신으로 업데이트
                                existingEvent.updateExternalEvent(summary, description, location, isAllDay, startDate, startDatetime, endDate, endDatetime);
                            }
                        } else {
                            // 케이스 C: 완전 신규 일정 -> 새로 생성
                            Events newEvent = Events.createExternalEvent(
                                    externalCalendar, googleEventId, summary, description, location, isAllDay, startDate, startDatetime, endDate, endDatetime
                            );
                            eventsToSave.add(newEvent);
                        }
                    }

                    if (!eventsToSave.isEmpty()) {
                        // 새로 생성이거나 되살아난 이벤트들 저장
                        eventsRepository.saveAll(eventsToSave);

                        List<UserEvents> newUserEvents = eventsToSave.stream()
                                .filter(event -> event.getCreatedAt() != null && event.getDeletedAt() == null)
                                .map(newEvent -> {
                                    boolean alreadyMapped = userEventsRepository.existsByUser_UserIdAndEvent_EventId(user.getUserId(), newEvent.getEventId());
                                    if (!alreadyMapped) {
                                        com.tryna.domain.label.entity.Labels finalLabel = labelsRepository.findByExternalCalendarAndDeletedAtIsNull(externalCalendar).orElse(null);
                                        return UserEvents.createOwner(user, newEvent, finalLabel);
                                    }
                                    return null;
                                })
                                .filter(Objects::nonNull)
                                .toList();

                        if (!newUserEvents.isEmpty()) {
                            userEventsRepository.saveAll(newUserEvents);
                        }
                    }
                });
            }

            // 2. 동기화 및 반영이 완벽히 끝난 후, 요청 시작 시 캡처해 둔 `requestStartWatermark`를 lastSyncedAt으로 안전하게 기록
            log.info("구글 캘린더 일정 동기화 완료 (조회 범위: {} ~ {}, 총 {}개 처리)", timeMin, timeMax, items.size());

            if (connectionId != null) {
                externalCalendarConnectionsRepository.findById(connectionId).ifPresent(conn -> {
                    conn.updateSyncStatus(requestStartWatermark, "SUCCESS");
                    externalCalendarConnectionsRepository.save(conn);
                });
            }
        } catch (BusinessException e) {
            markSyncFailed(userId);
            log.error("구글 캘린더 동기화 비즈니스 예외 발생 for user {}: {}", userId, e.getMessage());
            throw e;
        } catch (Exception e) {
            markSyncFailed(userId);
            log.error("구글 캘린더 동기화 처리 중 시스템 예외 발생 for user {}: {}", userId, e.getMessage(), e);
            throw new BusinessException(ExternalEventErrorCode.B105_EXTERNAL_EVENT_500);
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
                            conn.updateSyncStatus(conn.getLastSyncedAt(), "FAILED");
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

        // 미연동 케이스
        if (connectionOpt.isEmpty() || connectionOpt.get().getConnectionStatus() != com.tryna.domain.external.enums.ConnectionStatus.ACTIVE) {
            return new CalendarStatusResponse(false, null, null, "NONE", null, null);
        }

        // 연동 및 동기화 성공 케이스
        ExternalCalendarConnections conn = connectionOpt.get();
        String syncStatus = conn.getLastSyncStatus() == null ? "NONE" : conn.getLastSyncStatus();

        // 캘린더 이름 조회
        String calendarName = externalCalendarsRepository.findAllByConnection(conn)
                .stream().findFirst().map(ExternalCalendars::getName).orElse(null);

        return new CalendarStatusResponse(
                true,
                conn.getProvider().name(),
                calendarName,
                syncStatus,
                conn.getLastSyncedAt(),
                null
        );
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
                // 1. 라벨 이동
                labelsRepository.findByExternalCalendarAndDeletedAtIsNull(cal).ifPresent(extLabel -> {
                    Long sourceLabelId = extLabel.getLabelId();
                    userEventsRepository.moveLabelAssignments(userId, sourceLabelId, defaultLabel);
                });

                int deleted = labelsRepository.deleteByExternalCalendar(cal);
                if (deleted > 0) {
                    labelsRepository.flush();
                }

                // 2. 여기서 해당 캘린더의 외부 일정들을 Soft Delete
                eventsRepository.softDeleteByExternalCalendar(cal, LocalDateTime.now());
            }

            // 3. 외부 캘린더 삭제
            externalCalendarsRepository.deleteAll(calendars);
            externalCalendarConnectionsRepository.delete(connection);
        }

        log.info("유저 ID {}의 {} 캘린더 연동 정보 및 관련 일정/라벨 데이터 정리가 완료되었습니다.", userId, provider);
    }

    /**
     * 동시성 충돌 발생 시에도 안전하게 외부 캘린더 라벨을 조회하거나 생성하는 독립 트랜잭션 메서드
     */
    private com.tryna.domain.label.entity.Labels getOrCreateExternalLabel(TransactionTemplate requiresNewTemplate, Users user, ExternalCalendars externalCal, Long userId) {
        return labelsRepository.findByExternalCalendarAndDeletedAtIsNull(externalCal).orElseGet(() -> {
            try {
                // 저장(Insert) 시도만 REQUIRES_NEW 템플릿으로 감싸서 오염(Tainted) 방지
                return requiresNewTemplate.execute(status -> {
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
                });
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                String rootMessage = e.getMostSpecificCause().getMessage();
                if (rootMessage != null && rootMessage.contains("uq_labels_external_calendar_active")) {
                    return labelsRepository.findByExternalCalendarAndDeletedAtIsNull(externalCal)
                            .orElseThrow(() -> new BusinessException(ExternalEventErrorCode.B105_EXTERNAL_EVENT_500));
                } else {
                    throw e;
                }
            }
        });
    }

    /**
     * 해당 연도에 사용자의 가시적인 일정이 존재하는지 확인합니다.
     */
    private boolean hasEventsInYear(ExternalCalendars externalCalendar, Integer year) {
        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);

        return eventsRepository.existsByExternalCalendarAndDateRange(
                externalCalendar,
                startDate,
                endDate,
                EnumSet.of(EventStatus.CONFIRMED, EventStatus.NEEDS_CONFIRMATION)
        );
    }
}