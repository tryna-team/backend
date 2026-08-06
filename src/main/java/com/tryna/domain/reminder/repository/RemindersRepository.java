package com.tryna.domain.reminder.repository;

import com.tryna.domain.reminder.entity.Reminders;
import com.tryna.domain.reminder.enums.ReminderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface RemindersRepository extends JpaRepository<Reminders, Long> {

    boolean existsByTargetEvent_EventIdAndScheduledAtAndDeliveryChannelAndReminderStatus(
            Long eventId,
            LocalDateTime scheduledAt,
            String deliveryChannel,
            ReminderStatus reminderStatus
    );

    boolean existsByTargetActionItem_ActionItemIdAndScheduledAtAndDeliveryChannelAndReminderStatus(
            Long actionItemId,
            LocalDateTime scheduledAt,
            String deliveryChannel,
            ReminderStatus reminderStatus
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Reminders r
               SET r.reminderStatus = :nextStatus,
                   r.updatedAt = :updatedAt
             WHERE r.targetEvent.eventId = :eventId
               AND r.reminderStatus = :currentStatus
            """)
    int updateStatusForEvent(
            @Param("eventId") Long eventId,
            @Param("currentStatus") ReminderStatus currentStatus,
            @Param("nextStatus") ReminderStatus nextStatus,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Reminders r
               SET r.reminderStatus = :nextStatus,
                   r.updatedAt = :updatedAt
             WHERE r.targetActionItem.actionItemId = :actionItemId
               AND r.reminderStatus = :currentStatus
            """)
    int updateStatusForActionItem(
            @Param("actionItemId") Long actionItemId,
            @Param("currentStatus") ReminderStatus currentStatus,
            @Param("nextStatus") ReminderStatus nextStatus,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Reminders r
               SET r.reminderStatus = :nextStatus,
                   r.updatedAt = :updatedAt
             WHERE r.targetActionItem.parentEvent.eventId = :eventId
               AND r.reminderStatus = :currentStatus
            """)
    int updateStatusForActionItemsByParentEvent(
            @Param("eventId") Long eventId,
            @Param("currentStatus") ReminderStatus currentStatus,
            @Param("nextStatus") ReminderStatus nextStatus,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM Reminders r
             WHERE r.user.userId = :userId
            """)
    int deleteByUserId(@Param("userId") Long userId);
}
