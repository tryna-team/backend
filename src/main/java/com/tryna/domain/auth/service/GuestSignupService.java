package com.tryna.domain.auth.service;

import com.tryna.domain.label.entity.Labels;
import com.tryna.domain.label.enums.LabelColor;
import com.tryna.domain.label.repository.LabelsRepository;
import com.tryna.domain.user.entity.Users;
import com.tryna.domain.user.entity.UserSettings;
import com.tryna.domain.user.repository.UserRepository;
import com.tryna.domain.user.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class GuestSignupService {

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final LabelsRepository labelsRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Users registerNewGuest(String guestId) {
        // 1. 유저 생성
        Users targetUser = Users.createGuest(guestId);
        userRepository.save(targetUser);

        // 2. 기본 설정 생성
        UserSettings settings = UserSettings.createDefault(targetUser);
        userSettingsRepository.save(settings);

        // 3. Labels 엔티티의 정적 팩토리 메서드(createDefault)를 활용한 기본 라벨 생성
        String defaultLabelName = "기본";
        Labels defaultLabel = Labels.createDefault(
                targetUser,
                defaultLabelName,
                defaultLabelName.toLowerCase(Locale.ROOT),
                LabelColor.GREEN,
                1
        );
        labelsRepository.save(defaultLabel);

        return targetUser;
    }
}