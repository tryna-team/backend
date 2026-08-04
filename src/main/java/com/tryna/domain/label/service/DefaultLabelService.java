package com.tryna.domain.label.service;

import com.tryna.domain.label.entity.Labels;
import com.tryna.domain.label.enums.LabelColor;
import com.tryna.domain.label.repository.LabelsRepository;
import com.tryna.domain.user.entity.Users;
import lombok.RequiredArgsConstructor;
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
     * 충돌 발생 시 이 독립 트랜잭션만 롤백되며, 호출한 부모 트랜잭션에는 영향을 주지 않습니다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createDefaultLabel(Users user) {
        boolean hasDefaultLabel = labelsRepository.findByUser_UserIdAndIsDefaultTrue(user.getUserId()).isPresent();
        if (hasDefaultLabel) {
            return;
        }

        String rawNickname = (user.getNickname() != null && !user.getNickname().isBlank())
                ? user.getNickname().trim()
                : "사용자";

        if (rawNickname.length() > 96) {
            rawNickname = rawNickname.substring(0, 96);
        }

        String labelName = rawNickname + "의 라벨";
        String normalizedName = labelName.toLowerCase(Locale.ROOT);

        Labels defaultLabel = Labels.createDefault(
                user,
                labelName,
                normalizedName,
                LabelColor.GREEN,
                1
        );

        // saveAndFlush를 사용하여 제약조건 위반을 이 독립 트랜잭션 안에서 발생시킴
        labelsRepository.saveAndFlush(defaultLabel);
    }
}