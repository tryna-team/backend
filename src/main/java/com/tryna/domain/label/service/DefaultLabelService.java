package com.tryna.domain.label.service;

import com.tryna.domain.label.entity.Labels;
import com.tryna.domain.label.enums.LabelColor;
import com.tryna.domain.label.repository.LabelsRepository;
import com.tryna.domain.user.entity.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DefaultLabelService {

    private final LabelsRepository labelsRepository;

    /**
     * 기본 라벨을 생성합니다.
     * 외부(예: GuestSignupService)에서 호출 시 동일한 트랜잭션에 합류하여 원자성을 보장합니다.
     */
    @Transactional
    public void createDefaultLabel(Users user) {
        boolean hasDefaultLabel = labelsRepository.findByUser_UserIdAndIsDefaultTrue(user.getUserId()).isPresent();
        if (hasDefaultLabel) {
            return;
        }

        String defaultLabelName = "기본";
        String normalizedName = defaultLabelName.toLowerCase(Locale.ROOT);

        Labels defaultLabel = Labels.createDefault(
                user,
                defaultLabelName,
                normalizedName,
                LabelColor.GREEN,
                1
        );

        labelsRepository.save(defaultLabel);
    }
}