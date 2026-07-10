package com.tryna.domain.action.service;

import com.tryna.domain.action.dto.ActionItemSaveRequest;
import com.tryna.domain.action.dto.ActionItemSaveResponse;
import com.tryna.domain.action.entity.ActionItems;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        if (request.items().isEmpty()) {
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
                                ActionErrorCode.E105_ACTION_ITEM_403
                        )
                );

        // 3. 현재 사용자 조회
        Users user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new BusinessException(
                                ActionErrorCode.E105_ACTION_ITEM_403
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
                    ActionErrorCode.E105_ACTION_ITEM_403
            );
        }

        // 5. 준비/실행 항목의 유형별 필수값 검증
        validateActionItems(request.items());

        // 6. 요청 항목을 ActionItems 엔티티로 변환
        List<ActionItems> actionItems = request.items().stream()
                .map(item -> ActionItems.create(
                        event,
                        item.title(),
                        item.itemType(),
                        item.displayDate(),
                        item.displayDatetime(),
                        item.offsetDays(),
                        item.createdBy(),
                        item.sourceTemplateId()
                ))
                .toList();

        // 7. 준비/실행 항목 일괄 저장
        List<ActionItems> savedActionItems =
                actionItemsRepository.saveAll(actionItems);

        // 8. 피드백 로그를 RecommendationFeedbacks 엔티티로 변환
        List<RecommendationFeedbacks> feedbacks =
                request.feedbackLogs().stream()
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
                eventId,
                savedActionItems
        );
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
                                    || item.displayDatetime() != null;

                    case UNRESOLVED -> false;
                });

        if (hasInvalidItem) {
            throw new BusinessException(
                    ActionErrorCode.E105_ACTION_ITEM_400_2
            );
        }
    }
}