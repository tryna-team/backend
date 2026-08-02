package com.tryna.domain.label.service;

import com.tryna.domain.label.dto.LabelCreateRequest;
import com.tryna.domain.label.dto.LabelResponse;
import com.tryna.domain.label.entity.Labels;
import com.tryna.domain.label.repository.LabelsRepository;
import com.tryna.domain.user.entity.Users;
import com.tryna.domain.user.repository.UserRepository;
import com.tryna.global.exception.BusinessException;
import com.tryna.global.exception.CommonErrorCode;
import com.tryna.global.exception.LabelErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LabelService {

    private static final String DEFAULT_LABEL_COLOR = "#FF9500";
    private static final int MAX_LABEL_NAME_LENGTH = 100;

    private static final Pattern HEX_COLOR_PATTERN =
            Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private static final Pattern URL_PATTERN =
            Pattern.compile(
                    "^(https?://|www\\.).+",
                    Pattern.CASE_INSENSITIVE
            );

    private final LabelsRepository labelsRepository;
    private final UserRepository userRepository;

    /**
     * B108-2: 라벨 생성
     *
     * 현재 사용자가 입력한 이름과 색상을 검증한 뒤
     * USER 유형의 라벨을 생성합니다.
     *
     * 이름은 앞뒤 공백을 제거하고 소문자로 정규화하여
     * 동일 사용자의 활성 라벨 이름 중복 여부를 확인합니다.
     *
     * 색상이 전달되지 않으면 서버 기본 색상을 적용하고,
     * 새 라벨은 현재 사용자의 마지막 정렬 순서 다음에 배치합니다.
     *
     * @param userId 현재 인증된 사용자 ID
     * @param request 라벨 생성 요청
     * @return 생성된 라벨 정보
     */
    @Transactional
    public LabelResponse createLabel(
            Long userId,
            LabelCreateRequest request
    ) {
        // 1. 요청 객체 및 라벨 이름 검증
        if (request == null || request.name() == null) {
            throw new BusinessException(
                    LabelErrorCode.B108_LABEL_CREATE_400
            );
        }

        // 2. 라벨 이름 앞뒤 공백 제거
        String name = request.name().trim();

        // 3. 빈 이름 및 이름 길이 검증
        if (name.isBlank()
                || name.length() > MAX_LABEL_NAME_LENGTH) {
            throw new BusinessException(
                    LabelErrorCode.B108_LABEL_CREATE_400
            );
        }

        // 4. URL 형태의 이름 입력 방지
        if (URL_PATTERN.matcher(name).matches()) {
            throw new BusinessException(
                    LabelErrorCode.B108_LABEL_CREATE_400
            );
        }

        // 5. 라벨 색상 검증 및 기본값 적용
        String color = normalizeColor(request.color());

        // 6. 중복 검사용 라벨 이름 정규화
        String normalizedName = normalizeName(name);

        // 7. 현재 사용자 조회
        Users user = userRepository
                .findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() ->
                        new BusinessException(
                                CommonErrorCode.COMMON_403
                        )
                );

        // 8. 동일 사용자 내 활성 라벨 이름 중복 검증
        boolean duplicated =
                labelsRepository.existsByUser_UserIdAndNormalizedName(
                        userId,
                        normalizedName
                );

        if (duplicated) {
            throw new BusinessException(
                    LabelErrorCode.B108_LABEL_CREATE_409
            );
        }

        // 9. 새 라벨의 정렬 순서 계산
        Integer nextSortOrder = labelsRepository
                .findTopByUser_UserIdOrderBySortOrderDesc(userId)
                .map(label -> label.getSortOrder() + 1)
                .orElse(1);

        // 10. USER 유형 라벨 생성
        Labels label = Labels.createUserLabel(
                user,
                name,
                normalizedName,
                color,
                nextSortOrder
        );

        // 11. 라벨 저장
        try {
            Labels savedLabel = labelsRepository.saveAndFlush(label);
            return LabelResponse.from(savedLabel);
        } catch (DataIntegrityViolationException e) {
            // 동시 요청으로 유니크 인덱스 충돌이 발생한 경우
            throw new BusinessException(
                    LabelErrorCode.B108_LABEL_CREATE_409
            );
        }
    }

    /**
     * 라벨 이름을 중복 비교용 값으로 정규화합니다.
     *
     * 정책에 따라 앞뒤 공백을 제거하고,
     * 영문 대소문자를 구분하지 않도록 소문자로 변환합니다.
     *
     * @param name 사용자 입력 라벨 이름
     * @return 정규화된 라벨 이름
     */
    private String normalizeName(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 요청 색상을 검증하고 저장할 값을 반환합니다.
     *
     * 색상이 누락되거나 빈 문자열이면 서버 기본 색상을 사용합니다.
     *
     * @param color 요청 색상
     * @return 저장할 HEX 색상
     */
    private String normalizeColor(String color) {
        if (color == null || color.isBlank()) {
            return DEFAULT_LABEL_COLOR;
        }

        String normalizedColor = color.trim().toUpperCase(Locale.ROOT);

        if (!HEX_COLOR_PATTERN.matcher(normalizedColor).matches()) {
            throw new BusinessException(
                    LabelErrorCode.B108_LABEL_CREATE_400
            );
        }

        return normalizedColor;
    }
}