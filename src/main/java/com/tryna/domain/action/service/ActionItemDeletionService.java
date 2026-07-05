package com.tryna.domain.action.service;

import com.tryna.domain.action.repository.ActionItemsRepository;
import com.tryna.domain.reminder.service.ReminderLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActionItemDeletionService {

    private final ActionItemsRepository actionItemsRepository;
    private final ReminderLifecycleService reminderLifecycleService;

    @Transactional
    public boolean softDeleteActionItem(Long actionItemId) {
        int updated = actionItemsRepository.softDeleteById(actionItemId, LocalDateTime.now());
        if (updated <= 0) {
            return false;
        }

        reminderLifecycleService.cancelScheduledForSoftDeletedActionItem(actionItemId);
        return true;
    }
}
