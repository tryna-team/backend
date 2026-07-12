package com.tryna.domain.event.service;

import com.tryna.domain.event.repository.EventsRepository;
import com.tryna.domain.reminder.service.ReminderLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventDeletionService {

    private final EventsRepository eventsRepository;
    private final ReminderLifecycleService reminderLifecycleService;

    @Transactional
    public boolean softDeleteEvent(Long eventId) {
        int updated = eventsRepository.softDeleteById(eventId, LocalDateTime.now());
        if (updated <= 0) {
            return false;
        }

        reminderLifecycleService.cancelScheduledForSoftDeletedEvent(eventId);
        return true;
    }
}
