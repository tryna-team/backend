package com.tryna.domain.auth.service;

import com.tryna.domain.user.entity.Users;
import com.tryna.domain.user.entity.UserSettings;
import com.tryna.domain.user.repository.UserRepository;
import com.tryna.domain.user.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GuestSignupService {

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Users registerNewGuest(String guestId) {
        Users targetUser = Users.createGuest(guestId);
        userRepository.save(targetUser);

        UserSettings settings = UserSettings.createDefault(targetUser);
        userSettingsRepository.save(settings);

        return targetUser;
    }
}