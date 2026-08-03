package com.tryna.domain.label.service;

import com.tryna.domain.event.repository.UserEventsRepository;
import com.tryna.domain.label.dto.*;
import com.tryna.domain.label.entity.Labels;
import com.tryna.domain.label.enums.LabelColor;
import com.tryna.domain.label.enums.LabelType;
import com.tryna.domain.label.repository.LabelsRepository;
import com.tryna.domain.user.entity.Users;
import com.tryna.domain.user.repository.UserRepository;
import com.tryna.global.exception.BusinessException;
import com.tryna.global.exception.CommonErrorCode;
import com.tryna.global.exception.LabelErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LabelService {

    private static final LabelColor DEFAULT_LABEL_COLOR = LabelColor.GREEN;
    private static final int MAX_LABEL_NAME_LENGTH = 100;

    private static final Pattern URL_PATTERN =
            Pattern.compile(
                    "^(https?://|www\\.).+",
                    Pattern.CASE_INSENSITIVE
            );

    private final LabelsRepository labelsRepository;
    private final UserRepository userRepository;
    private final UserEventsRepository userEventsRepository;

    /**
     * B108-1: 라벨 목록 조회
     *
     * 현재 사용자가 소유한 활성 라벨을 정렬 순서대로 조회합니다.
     *
     * 기본 라벨, 사용자 라벨, 외부 캘린더 라벨을 모두 반환하며,
     * Labels 엔티티의 Soft Delete 조건에 따라 삭제된 라벨은 제외됩니다.
     *
     * @param userId 현재 인증된 사용자 ID
     * @return 현재 사용자의 라벨 목록
     */
    public LabelListResponse getLabels(
            Long userId
    ) {
        // 1. 현재 사용자가 존재하는지 확인
        userRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() ->
                        new BusinessException(
                                CommonErrorCode.COMMON_403
                        )
                );

        // 2. 현재 사용자의 활성 라벨을 정렬 순서대로 조회
        List<Labels> labels =
                labelsRepository.findAllByUser_UserIdOrderBySortOrderAsc(
                        userId
                );

        // 3. 조회 결과를 응답 DTO로 변환
        return LabelListResponse.from(labels);
    }

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
        LabelColor color = resolveCreateColor(request.color());

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
     * B108-3: 라벨 수정
     *
     * 현재 사용자가 소유한 활성 라벨의 이름, 색상, 표시 여부를 수정합니다.
     *
     * 기본 라벨과 외부 캘린더 라벨도 이름과 색상을 수정할 수 있지만,
     * labelType, isDefault, externalCalendarId는 변경하지 않습니다.
     *
     * @param userId 현재 인증된 사용자 ID
     * @param labelId 수정할 라벨 ID
     * @param request 라벨 수정 요청
     * @return 수정된 라벨 정보
     */
    @Transactional
    public LabelResponse updateLabel(
            Long userId,
            Long labelId,
            LabelUpdateRequest request
    ) {
        // 1. 수정할 값이 하나 이상 있는지 확인
        if (request == null || request.hasNoChanges()) {
            throw new BusinessException(
                    LabelErrorCode.B108_LABEL_UPDATE_400
            );
        }

        // 2. 삭제되지 않은 라벨 조회
        Labels label = labelsRepository.findById(labelId)
                .orElseThrow(() ->
                        new BusinessException(
                                LabelErrorCode.B108_LABEL_UPDATE_404
                        )
                );

        // 3. 현재 사용자가 소유한 라벨인지 확인
        if (!label.getUser().getUserId().equals(userId)) {
            throw new BusinessException(
                    LabelErrorCode.B108_LABEL_UPDATE_403
            );
        }

        // 4. 이름 수정값 검증
        String name = null;
        String normalizedName = null;

        if (request.name() != null) {
            name = request.name().trim();

            if (name.isBlank()
                    || name.length() > MAX_LABEL_NAME_LENGTH
                    || URL_PATTERN.matcher(name).matches()) {
                throw new BusinessException(
                        LabelErrorCode.B108_LABEL_UPDATE_400
                );
            }

            normalizedName = normalizeName(name);

            boolean duplicated = labelsRepository
                    .existsByUser_UserIdAndNormalizedNameAndLabelIdNot(
                            userId,
                            normalizedName,
                            labelId
                    );

            if (duplicated) {
                throw new BusinessException(
                        LabelErrorCode.B108_LABEL_UPDATE_409
                );
            }
        }

        // 5. 색상 수정값 확인
        // null이면 기존 색상을 유지하고, 값이 있으면 요청 색상으로 변경
        LabelColor color = request.color();

        // 6. 요청에 포함된 값만 수정
        label.update(
                name,
                normalizedName,
                color,
                request.isVisible()
        );

        // 7. 더티 체킹으로 저장 후 반환
        return LabelResponse.from(label);
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
     * @return 저장할 색상
     */
    private LabelColor resolveCreateColor(LabelColor color) {
        return color == null
                ? DEFAULT_LABEL_COLOR
                : color;
    }

    /**
     * B108-4: 라벨 삭제
     *
     * 현재 사용자가 소유한 활성 라벨을 삭제합니다.
     *
     * 마지막 활성 라벨은 삭제할 수 없습니다.
     * 기본 라벨을 삭제하는 경우 남은 활성 라벨 중 정렬 순서가 가장 앞선
     * 라벨을 새 기본 라벨로 지정하고, 삭제 대상 일정도 해당 라벨로 이동합니다.
     *
     * 기본 라벨 변경, 일정 이동, Soft Delete 및 정렬 순서 정규화는
     * 하나의 트랜잭션으로 처리합니다.
     *
     * @param userId 현재 인증된 사용자 ID
     * @param labelId 삭제할 라벨 ID
     * @return 라벨 삭제 및 일정 이동 결과
     */
    @Transactional
    public LabelDeleteResponse deleteLabel(
            Long userId,
            Long labelId
    ) {
        // 1. 삭제되지 않은 라벨 조회
        Labels label = labelsRepository.findById(labelId)
                .orElseThrow(() ->
                        new BusinessException(
                                LabelErrorCode.B108_LABEL_DELETE_404
                        )
                );

        // 2. 현재 사용자가 소유한 라벨인지 확인
        if (!label.getUser().getUserId().equals(userId)) {
            throw new BusinessException(
                    LabelErrorCode.B108_LABEL_DELETE_403
            );
        }

        boolean defaultLabelChanged =
                Boolean.TRUE.equals(label.getIsDefault());

        // 3. 외부 캘린더 라벨 삭제 방지
        if (label.getLabelType() == LabelType.EXTERNAL_CALENDAR) {
            throw new BusinessException(
                    LabelErrorCode.B108_LABEL_DELETE_400
            );
        }

        // 4. 현재 사용자의 활성 Tryna 라벨 조회
        List<Labels> activeLabels = labelsRepository
                .findAllByUser_UserIdOrderBySortOrderAsc(userId)
                .stream()
                .filter(activeLabel ->
                        activeLabel.getLabelType() != LabelType.EXTERNAL_CALENDAR
                )
                .toList();

        // 5. 마지막 활성 라벨 삭제 방지
        if (activeLabels.size() <= 1) {
            throw new BusinessException(
                    LabelErrorCode.B108_LABEL_DELETE_400
            );
        }

        // 6. 삭제 후 기본 라벨 결정
        Labels destinationLabel;

        if (Boolean.TRUE.equals(label.getIsDefault())) {
            destinationLabel = activeLabels.stream()
                    .filter(activeLabel ->
                            Boolean.TRUE.equals(activeLabel.getIsDefault())
                    )
                    .findFirst()
                    .orElseThrow(() ->
                            new BusinessException(
                                    LabelErrorCode.B108_LABEL_DELETE_409
                            )
                    );

            // 삭제할 기존 기본 라벨 해제
            label.updateDefault(false);
            labelsRepository.flush();

            // 남은 라벨 중 가장 앞선 라벨을 새 기본 라벨로 지정
            destinationLabel.updateDefault(true);
        } else {
            destinationLabel = activeLabels.stream()
                    .filter(Labels::getIsDefault)
                    .findFirst()
                    .orElseThrow(() ->
                            new BusinessException(
                                    LabelErrorCode.B108_LABEL_DELETE_409
                            )
                    );
        }

        // 7. 삭제 전 정렬 순서 저장
        Integer deletedSortOrder = label.getSortOrder();

        // 8. 삭제 대상 라벨에 연결된 일정을 기본 라벨로 이동
        int movedEventCount = userEventsRepository.moveLabelAssignments(
                userId,
                labelId,
                destinationLabel
        );

        // 9. 라벨 Soft Delete
        label.softDelete(LocalDateTime.now());

        // 10. 남은 라벨의 정렬 순서 정규화
        closeSortOrderGap(
                userId,
                labelId,
                deletedSortOrder
        );

        // 11. 삭제 결과 반환
        return LabelDeleteResponse.of(
                labelId,
                movedEventCount,
                destinationLabel.getLabelId(),
                defaultLabelChanged
        );
    }

    /**
     * 라벨 삭제 후 비어 있는 정렬 순서를 제거합니다.
     *
     * 삭제된 라벨보다 뒤에 있던 활성 라벨의 sortOrder를
     * 각각 1씩 감소시켜 연속된 순서를 유지합니다.
     *
     * @param userId 현재 사용자 ID
     * @param deletedLabelId 삭제한 라벨 ID
     * @param deletedSortOrder 삭제한 라벨의 기존 정렬 순서
     */
    private void closeSortOrderGap(
            Long userId,
            Long deletedLabelId,
            Integer deletedSortOrder
    ) {
        List<Labels> labels = labelsRepository
                .findAllByUser_UserIdOrderBySortOrderAsc(userId);

        labels.stream()
                .filter(label ->
                        !label.getLabelId().equals(deletedLabelId)
                )
                .filter(label ->
                        label.getSortOrder() > deletedSortOrder
                )
                .forEach(label ->
                        label.updateSortOrder(
                                label.getSortOrder() - 1
                        )
                );
    }
}