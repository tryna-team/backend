package com.tryna.domain.reminder.service;

import com.tryna.domain.reminder.repository.AlarmDelayedQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis 지연 큐(ZSET)를 주기적으로 폴링하여 발송 시각이 도래한 리마인더를 FCM으로 발송하는 컨슈머.
 *
 * - 발송 대상이 이미 삭제되었거나(soft delete), 준비/실행 항목이 완료 처리된 경우에는 발송하지 않는다. (Soft Skip)
 * - 반복 일정/반복 일정에 연결된 준비·실행 항목의 알람은 발송 직후 다음 회차로 재계산하여 다시 큐에 적재한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmReminderDispatcher {

    private static final int BATCH_SIZE = 50;

    private final AlarmDelayedQueueRepository alarmDelayedQueueRepository;
    private final AlarmReminderDispatchExecutor alarmReminderDispatchExecutor;

    @Scheduled(fixedDelay = 15000)
    public void dispatchDueReminders() {
        List<Long> dueReminderIds = alarmDelayedQueueRepository.popDue(BATCH_SIZE);

        for (Long reminderId : dueReminderIds) {
            try {
                alarmReminderDispatchExecutor.dispatchOne(reminderId);
            } catch (Exception e) {
                log.error("리마인더 발송 처리 중 오류가 발생했습니다. reminderId={}", reminderId, e);
                alarmDelayedQueueRepository.reEnqueue(reminderId);
            }
        }
    }
}
