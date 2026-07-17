package com.tryna.domain.action.service;

import com.tryna.domain.action.dto.*;
import com.tryna.domain.action.entity.ActionItems;
import com.tryna.domain.action.enums.ActionItemStatus;
import com.tryna.domain.action.enums.ItemType;
import com.tryna.domain.action.repository.ActionItemsRepository;
import com.tryna.domain.event.entity.Events;
import com.tryna.domain.event.repository.EventsRepository;
import com.tryna.domain.event.repository.UserEventsRepository;
import com.tryna.domain.recommendation.entity.mapping.RecommendationFeedbacks;
import com.tryna.domain.recommendation.repository.RecommendationFeedbacksRepository;
import com.tryna.domain.user.entity.Users;
import com.tryna.domain.user.repository.UserRepository;
import com.tryna.global.exception.ActionErrorCode;
import com.tryna.global.exception.BusinessException;
import com.tryna.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActionItemService {

    private final ActionItemsRepository actionItemsRepository;
    private final RecommendationFeedbacksRepository recommendationFeedbacksRepository;
    private final EventsRepository eventsRepository;
    private final UserEventsRepository userEventsRepository;
    private final UserRepository userRepository;

    /**
     * E105: 준비/실행 항목 일괄 저장
     *
     * 프론트엔드에서 최종 확정한 준비/실행 항목과
     * E101~E104 과정에서 발생한 피드백 로그를 하나의 트랜잭션으로 저장합니다.
     *
     * 준비/실행 항목 저장 또는 피드백 로그 저장 중 하나라도 실패하면
     * 전체 저장 작업을 롤백합니다.
     *
     * @param userId  현재 인증된 사용자 ID
     * @param eventId 준비/실행 항목을 연결할 일정 ID
     * @param request 저장할 항목 및 피드백 로그 요청
     * @return 저장된 준비/실행 항목 정보
     */
    @Transactional
    public ActionItemSaveResponse saveActionItems(
            Long userId,
            Long eventId,
            ActionItemSaveRequest request
    ) {
        // 1. 저장할 준비/실행 항목이 존재하는지 확인
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new BusinessException(
                    ActionErrorCode.E105_ACTION_ITEM_400_1
            );
        }

        // 2. 일정 조회
        //
        // 존재하지 않는 일정과 접근할 수 없는 일정을 구분하여 노출하지 않고
        // 동일한 권한 오류로 처리합니다.
        Events event = eventsRepository.findById(eventId)
                .orElseThrow(() ->
                        new BusinessException(
                                CommonErrorCode.COMMON_403
                        )
                );

        // 3. 현재 사용자 조회
        Users user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new BusinessException(
                                CommonErrorCode.COMMON_403
                        )
                );

        // 4. user_events 연결 정보를 기준으로 일정 접근 권한 확인
        boolean hasEventAccess =
                userEventsRepository.existsByUser_UserIdAndEvent_EventId(
                        userId,
                        eventId
                );

        if (!hasEventAccess) {
            throw new BusinessException(
                    CommonErrorCode.COMMON_403
            );
        }

        // 5. 실제 저장 로직은 C104에서도 재사용할 수 있도록 공통 메서드로 위임
        return saveActionItemsForEvent(user, event, request);
    }

    @Transactional
    public ActionItemSaveResponse saveActionItemsForEvent(
            Users user,
            Events event,
            ActionItemSaveRequest request
    ) {
        // C104에서는 선택 항목이 없어도 피드백 로그는 저장될 수 있다.
        List<ActionItems> savedActionItems = List.of();

        if (request != null && request.items() != null && !request.items().isEmpty()) {
            validateActionItems(request.items());

            // 6. 요청 항목을 ActionItems 엔티티로 변환
            List<ActionItems> actionItems = request.items().stream()
                    .map(item -> ActionItems.create(
                            event,
                            item.title(),
                            item.itemType(),
                            item.displayDate(),
                            item.displayTime(),
                            item.offsetDays(),
                            item.createdBy(),
                            item.sourceTemplateId()
                    ))
                    .toList();

            // 7. 준비/실행 항목 일괄 저장
            savedActionItems = actionItemsRepository.saveAll(actionItems);
        }

        // 8. 피드백 로그를 RecommendationFeedbacks 엔티티로 변환
        List<RecommendationFeedbacks> feedbacks =
                safeFeedbackLogs(request).stream()
                        .map(feedback -> RecommendationFeedbacks.create(
                                user,
                                event,
                                feedback.sourceTemplateId(),
                                feedback.actionType(),
                                feedback.originalTitle(),
                                feedback.editedTitle(),
                                feedback.reason()
                        ))
                        .toList();

        // 9. 피드백 로그가 존재하는 경우 일괄 저장
        if (!feedbacks.isEmpty()) {
            recommendationFeedbacksRepository.saveAll(feedbacks);
        }

        // 10. 저장된 준비/실행 항목을 응답 DTO로 변환하여 반환
        return ActionItemSaveResponse.from(
                event.getEventId(),
                savedActionItems
        );
    }

    private List<ActionItemSaveRequest.Feedback> safeFeedbackLogs(
            ActionItemSaveRequest request
    ) {
        if (request == null || request.feedbackLogs() == null) {
            return List.of();
        }
        return request.feedbackLogs();
    }

    /**
     * E105 요청 항목의 유형별 필수값을 검증합니다.
     *
     * TIMED_ACTION은 캘린더에 표시되어야 하므로
     * displayDate가 반드시 존재해야 합니다.
     *
     * UNTIMED_PREP는 날짜와 시간을 가지면 안 됩니다.
     *
     * @param items 저장 요청 항목 목록
     */
    private void validateActionItems(
            List<ActionItemSaveRequest.Item> items
    ) {
        boolean hasInvalidItem = items.stream()
                .anyMatch(item -> switch (item.itemType()) {
                    case TIMED_ACTION ->
                            item.displayDate() == null;

                    case UNTIMED_PREP ->
                            item.displayDate() != null
                                    || item.displayTime() != null;

                    case UNRESOLVED -> false;
                });

        if (hasInvalidItem) {
            throw new BusinessException(
                    ActionErrorCode.E105_ACTION_ITEM_400_2
            );
        }
    }

    /**
     * E106: 준비/실행 항목 완료 상태 변경
     *
     * 준비/실행 항목이 존재하는지 확인하고,
     * 현재 사용자가 해당 항목의 부모 일정에 연결되어 있는지 검증한 뒤
     * 항목 상태를 COMPLETED 또는 PENDING으로 변경합니다.
     *
     * @param userId 현재 인증된 사용자 ID
     * @param actionItemId 상태를 변경할 준비/실행 항목 ID
     * @param request 변경할 상태 정보
     * @return 상태 변경 결과
     */
    @Transactional
    public ActionItemStatusUpdateResponse updateActionItemStatus(
            Long userId,
            Long actionItemId,
            ActionItemStatusUpdateRequest request
    ) {
        // 1. E106에서는 완료 처리와 완료 취소만 허용
        ActionItemStatus requestedStatus = request.actionItemStatus();

        if (requestedStatus != ActionItemStatus.COMPLETED
                && requestedStatus != ActionItemStatus.PENDING) {
            throw new BusinessException(
                    ActionErrorCode.E106_ACTION_ITEM_400
            );
        }

        // 2. 삭제되지 않은 준비/실행 항목 조회
        ActionItems actionItem = actionItemsRepository
                .findByActionItemIdAndDeletedAtIsNull(actionItemId)
                .orElseThrow(() ->
                        new BusinessException(
                                ActionErrorCode.E106_ACTION_ITEM_404
                        )
                );

        // 3. 준비/실행 항목의 부모 일정 ID 조회
        Long eventId = actionItem
                .getParentEvent()
                .getEventId();

        // 4. user_events 연결 정보를 기준으로 일정 접근 권한 확인
        boolean hasEventAccess =
                userEventsRepository.existsByUser_UserIdAndEvent_EventId(
                        userId,
                        eventId
                );

        if (!hasEventAccess) {
            throw new BusinessException(
                    CommonErrorCode.COMMON_403
            );
        }

        // 5. 엔티티 상태 및 완료 일시 변경
        actionItem.updateStatus(requestedStatus);

        // 6. JPA 더티 체킹으로 변경사항 저장 후 응답 반환
        return ActionItemStatusUpdateResponse.from(actionItem);
    }

    /**
     * F103: 일정 상세 내 준비/실행 항목 조회
     *
     * 일정 존재 여부와 현재 사용자의 일정 접근 권한을 확인한 뒤,
     * 해당 일정에 연결된 삭제되지 않은 준비/실행 항목을 반환합니다.
     *
     * @param userId 현재 인증된 사용자 ID
     * @param eventId 조회할 일정 ID
     * @return 일정 상세 내 준비/실행 항목 목록
     */
    public EventActionItemResponse getEventActionItems(
            Long userId,
            Long eventId
    ) {
        // 1. 일정 존재 여부 확인
        if (!eventsRepository.existsById(eventId)) {
            throw new BusinessException(
                    ActionErrorCode.F103_ACTION_ITEM_404
            );
        }

        // 2. user_events 연결 정보를 기준으로 일정 접근 권한 확인
        boolean hasEventAccess =
                userEventsRepository.existsByUser_UserIdAndEvent_EventId(
                        userId,
                        eventId
                );

        if (!hasEventAccess) {
            throw new BusinessException(
                    CommonErrorCode.COMMON_403
            );
        }

        // 3. 일정에 연결된 삭제되지 않은 준비/실행 항목 조회
        List<ActionItems> actionItems = actionItemsRepository
                .findAllByParentEvent_EventIdAndDeletedAtIsNullOrderByDisplayDateAscDisplayDatetimeAscActionItemIdAsc(
                        eventId
                );

        // 4. 조회 결과를 응답 DTO로 변환하여 반환
        return EventActionItemResponse.from(
                eventId,
                actionItems
        );
    }

    /**
     * F104: 캘린더 내 시간형 실행 항목 조회
     *
     * 요청 날짜를 검증한 뒤 현재 사용자의 일정에 연결된 항목 중
     * displayDate가 일치하는 TIMED_ACTION 항목만 반환합니다.
     *
     * @param userId 현재 인증된 사용자 ID
     * @param dateText 조회 날짜 문자열
     * @return 선택한 날짜의 시간형 실행 항목 목록
     */
    public TimedActionItemResponse getTimedActionItems(
            Long userId,
            String dateText
    ) {
        // 1. yyyy-MM-dd 형식의 조회 날짜 검증
        LocalDate date;

        try {
            date = LocalDate.parse(dateText);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new BusinessException(
                    ActionErrorCode.F104_ACTION_ITEM_400
            );
        }

        // 2. 현재 사용자의 시간형 실행 항목 조회
        List<ActionItems> actionItems = actionItemsRepository
                .findCalendarActionItemsByDate(
                        userId,
                        date,
                        ItemType.TIMED_ACTION
                );

        // 3. 응답 DTO로 변환
        return TimedActionItemResponse.from(
                date,
                actionItems
        );
    }
}
