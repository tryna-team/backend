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
     * 독립된 트랜잭션(REQUIRES_NEW)으로 기본 라벨 생성을 시도합니다.
     * 동시성 충돌(uq_labels_user_default_active 제약조건 위반) 발생 시
     * 예외를 삼키고 안전하게 멱등성(Idempotent)을 보장합니다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createDefaultLabel(Users user) {
        boolean hasDefaultLabel = labelsRepository.findByUser_UserIdAndIsDefaultTrue(user.getUserId()).isPresent();
        if (hasDefaultLabel) {
            return;
        }

        try {
            String defaultLabelName = "기본";
            String normalizedName = defaultLabelName.toLowerCase(Locale.ROOT);

            Labels defaultLabel = Labels.createDefault(
                    user,
                    defaultLabelName,
                    normalizedName,
                    LabelColor.GREEN,
                    1
            );

            // saveAndFlush를 사용하여 제약조건 위반을 이 독립 트랜잭션 안에서 발생시킴
            labelsRepository.saveAndFlush(defaultLabel);
        } catch (DataIntegrityViolationException e) {
            String rootMessage = e.getMostSpecificCause().getMessage();
            // 기본 라벨 유니크 제약조건 충돌인 경우, 다른 트랜잭션에서 이미 생성한 것이므로 안전하게 무시
            if (rootMessage == null || !rootMessage.contains("uq_labels_user_default_active")) {
                throw e;
            }
        }
    }
}