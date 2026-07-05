package com.tryna.domain.reminder.service;

import com.tryna.domain.reminder.enums.ReminderStatus;
import com.tryna.domain.reminder.repository.RemindersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReminderLifecycleService {

    private final RemindersRepository remindersRepository;

    // events/action_items가 Soft Delete 되면 FK CASCADE가 동작하지 않으므로
    // 스케줄링된 리마인더를 명시적으로 취소 처리한다.
    @Transactional
    public int cancelScheduledForSoftDeletedEvent(Long eventId) {
        return remindersRepository.updateStatusForEvent(
                eventId,
                ReminderStatus.SCHEDULED,
                ReminderStatus.CANCELED,
                LocalDateTime.now()
        );
    }

    @Transactional
    public int cancelScheduledForSoftDeletedActionItem(Long actionItemId) {
        return remindersRepository.updateStatusForActionItem(
                actionItemId,
                ReminderStatus.SCHEDULED,
                ReminderStatus.CANCELED,
                LocalDateTime.now()
        );
    }

    public boolean isAlreadyScheduledForEvent(Long eventId, LocalDateTime scheduledAt, String deliveryChannel) {
        return remindersRepository.existsByTargetEvent_EventIdAndScheduledAtAndDeliveryChannelAndReminderStatus(
                eventId,
                scheduledAt,
                deliveryChannel,
                ReminderStatus.SCHEDULED
        );
    }

    public boolean isAlreadyScheduledForActionItem(Long actionItemId, LocalDateTime scheduledAt, String deliveryChannel) {
        return remindersRepository.existsByTargetActionItem_ActionItemIdAndScheduledAtAndDeliveryChannelAndReminderStatus(
                actionItemId,
                scheduledAt,
                deliveryChannel,
                ReminderStatus.SCHEDULED
        );
    }
}
