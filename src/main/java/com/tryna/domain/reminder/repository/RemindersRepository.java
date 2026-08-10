package com.tryna.domain.reminder.repository;

import com.tryna.domain.reminder.entity.Reminders;
import com.tryna.domain.reminder.enums.ReminderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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
            UPDATE Reminders r
               SET r.reminderStatus = :nextStatus,
                   r.updatedAt = :updatedAt
             WHERE r.targetActionItem.parentEvent.eventId = :eventId
               AND r.targetActionItem.displayDate = :displayDate
               AND r.reminderStatus = :currentStatus
            """)
    int updateStatusForActionItemsByParentEventAndDisplayDate(
            @Param("eventId") Long eventId,
            @Param("displayDate") LocalDate displayDate,
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
               AND r.targetActionItem.displayDate >= :displayDate
               AND r.reminderStatus = :currentStatus
            """)
    int updateStatusForActionItemsByParentEventFromDisplayDate(
            @Param("eventId") Long eventId,
            @Param("displayDate") LocalDate displayDate,
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

    // 부모 일정이 Soft Delete 될 때, 해당 일정 및 하위 실행 항목들에 걸려있는 리마인드를 한 번에 물리 삭제
    @Modifying(flushAutomatically = true)
    @Query(value = """
            DELETE FROM reminders r
             WHERE r.target_event_id = :eventId
                OR r.target_action_item_id IN (
                    SELECT action_item_id FROM action_items WHERE parent_event_id = :eventId
                )
            """, nativeQuery = true)
    int deleteByEventIdCascade(@Param("eventId") Long eventId);
}
