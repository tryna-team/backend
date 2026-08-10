package com.tryna.domain.reminder.repository;

import com.tryna.domain.reminder.entity.Reminders;
import com.tryna.domain.reminder.enums.ReminderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface RemindersRepository extends JpaRepository<Reminders, Long> {

    // F101/F102: 일정·준비/실행 항목에 이미 활성화된 리마인더가 있는지 확인 (409 처리용)
    boolean existsByTargetEvent_EventIdAndReminderStatus(
            Long eventId,
            ReminderStatus reminderStatus
    );

    boolean existsByTargetActionItem_ActionItemIdAndReminderStatus(
            Long actionItemId,
            ReminderStatus reminderStatus
    );

    // F100(알람 수정)/완료 취소: 대상별 활성 리마인더 조회
    List<Reminders> findAllByTargetEvent_EventIdAndReminderStatus(
            Long eventId,
            ReminderStatus reminderStatus
    );

    List<Reminders> findAllByTargetActionItem_ActionItemIdAndReminderStatus(
            Long actionItemId,
            ReminderStatus reminderStatus
    );

    List<Reminders> findAllByTargetActionItem_ParentEvent_EventIdAndReminderStatus(
            Long eventId,
            ReminderStatus reminderStatus
    );

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
}
