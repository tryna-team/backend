package com.tryna.domain.alarm.service;

import com.tryna.domain.alarm.dto.AlarmDetailResponse;
import com.tryna.domain.alarm.dto.AlarmListResponse;
import com.tryna.domain.alarm.util.AlarmCursorCodec;
import com.tryna.domain.reminder.entity.Reminders;
import com.tryna.domain.reminder.repository.RemindersRepository;
import com.tryna.domain.user.entity.Users;
import com.tryna.domain.user.repository.UserRepository;
import com.tryna.global.exception.AlarmErrorCode;
import com.tryna.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlarmQueryService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;

    private final RemindersRepository remindersRepository;
    private final UserRepository userRepository;

    public AlarmDetailResponse getAlarm(Long userId, Long reminderId) {
        validateActiveUser(userId);

        Reminders reminder = remindersRepository.findByReminderIdAndUser_UserId(reminderId, userId)
                .orElseThrow(() -> new BusinessException(AlarmErrorCode.F100_ALARM_404));

        return AlarmDetailResponse.from(reminder);
    }

    public AlarmListResponse getMyAlarms(Long userId, Integer size, String cursor) {
        validateActiveUser(userId);

        int pageSize = resolvePageSize(size);
        AlarmCursorCodec.Cursor decodedCursor = decodeCursor(cursor);

        try {
            List<Reminders> fetchedReminders = remindersRepository.findByUserIdWithCursor(
                    userId,
                    decodedCursor == null ? null : decodedCursor.updatedAt(),
                    decodedCursor == null ? null : decodedCursor.reminderId(),
                    PageRequest.of(0, pageSize + 1)
            );

            boolean hasNext = fetchedReminders.size() > pageSize;
            List<Reminders> pageReminders = hasNext
                    ? fetchedReminders.subList(0, pageSize)
                    : fetchedReminders;

            List<AlarmDetailResponse> alarms = pageReminders.stream()
                    .map(AlarmDetailResponse::from)
                    .toList();

            String nextCursor = null;
            if (hasNext && !pageReminders.isEmpty()) {
                Reminders lastReminder = pageReminders.get(pageReminders.size() - 1);
                nextCursor = AlarmCursorCodec.encode(
                        new AlarmCursorCodec.Cursor(lastReminder.getUpdatedAt(), lastReminder.getReminderId())
                );
            }

            return AlarmListResponse.of(
                    new ArrayList<>(alarms),
                    new AlarmListResponse.PageInfo(hasNext, nextCursor)
            );
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(AlarmErrorCode.F100_ALARMLIST_500);
        }
    }

    private void validateActiveUser(Long userId) {
        Users user = userRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(AlarmErrorCode.F100_ALARMLIST_409));
    }

    private int resolvePageSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }

        if (size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            throw new BusinessException(AlarmErrorCode.F100_ALARMLIST_400_1);
        }

        return size;
    }

    private AlarmCursorCodec.Cursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }

        return AlarmCursorCodec.decode(cursor);
    }
}
