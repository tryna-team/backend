package com.tryna.domain.auth.service;

import com.tryna.domain.label.service.DefaultLabelService;
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
    private final DefaultLabelService defaultLabelService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Users registerNewGuest(String guestId) {
        // 1. 유저 생성
        Users targetUser = Users.createGuest(guestId);
        userRepository.save(targetUser);

        // 2. 기본 설정 생성
        UserSettings settings = UserSettings.createDefault(targetUser);
        userSettingsRepository.save(settings);

        // 3. 기본 라벨 생성 (유저, 설정과 함께 무조건 한 세트로 커밋/롤백되도록 원자성 보장)
        defaultLabelService.createDefaultLabel(targetUser);

        return targetUser;
    }
}