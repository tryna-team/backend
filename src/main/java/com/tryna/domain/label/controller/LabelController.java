package com.tryna.domain.label.controller;

import com.tryna.domain.label.controller.docs.LabelControllerDocs;
import com.tryna.domain.label.dto.*;
import com.tryna.domain.label.service.LabelService;
import com.tryna.global.exception.AuthErrorCode;
import com.tryna.global.exception.BusinessException;
import com.tryna.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/labels")
@RequiredArgsConstructor
public class LabelController implements LabelControllerDocs {

    private final LabelService labelService;

    /**
     * B108-1: 라벨 목록 조회
     */
    @GetMapping
    @Override
    public ResponseEntity<ApiResponse<LabelListResponse>> getLabels() {
        // 1. SecurityContext에서 현재 사용자 ID 추출
        Long userId = extractUserIdFromSecurityContext();

        // 2. 인증 정보가 없는 경우 인증 예외 처리
        if (userId == null) {
            throw new BusinessException(
                    AuthErrorCode.AUTH_401
            );
        }

        // 3. 현재 사용자의 라벨 목록 조회
        LabelListResponse response =
                labelService.getLabels(userId);

        // 4. 공통 응답 형식으로 반환
        return ResponseEntity.ok(
                ApiResponse.success(
                        "B108_LABEL_LIST_200",
                        "라벨 목록 조회에 성공했습니다.",
                        response
                )
        );
    }

    /**
     * B108-2: 라벨 생성
     */
    @PostMapping
    @Override
    public ResponseEntity<ApiResponse<LabelResponse>> createLabel(
            @RequestBody LabelCreateRequest request
    ) {
        // 1. SecurityContext에서 현재 사용자 ID 추출
        Long userId = extractUserIdFromSecurityContext();

        // 2. 인증 정보가 없는 경우 인증 예외 처리
        if (userId == null) {
            throw new BusinessException(
                    AuthErrorCode.AUTH_401
            );
        }

        // 3. 사용자 라벨 생성
        LabelResponse response =
                labelService.createLabel(userId, request);

        // 4. 공통 응답 형식으로 반환
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                "B108_LABEL_CREATE_201",
                                "라벨이 생성되었습니다.",
                                response
                        )
                );
    }

    /**
     * B108-3: 라벨 수정
     */
    @PatchMapping("/{labelId}")
    @Override
    public ResponseEntity<ApiResponse<LabelResponse>> updateLabel(
            @PathVariable("labelId") Long labelId,
            @RequestBody LabelUpdateRequest request
    ) {
        // 1. SecurityContext에서 현재 사용자 ID 추출
        Long userId = extractUserIdFromSecurityContext();

        // 2. 인증 정보가 없는 경우 인증 예외 처리
        if (userId == null) {
            throw new BusinessException(
                    AuthErrorCode.AUTH_401
            );
        }

        // 3. 라벨 수정
        LabelResponse response = labelService.updateLabel(
                userId,
                labelId,
                request
        );

        // 4. 공통 응답 형식으로 반환
        return ResponseEntity.ok(
                ApiResponse.success(
                        "B108_LABEL_UPDATE_200",
                        "라벨이 수정되었습니다.",
                        response
                )
        );
    }

    /**
     * B108-4: 라벨 삭제
     */
    @DeleteMapping("/{labelId}")
    @Override
    public ResponseEntity<ApiResponse<LabelDeleteResponse>> deleteLabel(
            @PathVariable("labelId") Long labelId
    ) {
        // 1. SecurityContext에서 현재 사용자 ID 추출
        Long userId = extractUserIdFromSecurityContext();

        // 2. 인증 정보가 없는 경우 인증 예외 처리
        if (userId == null) {
            throw new BusinessException(
                    AuthErrorCode.AUTH_401
            );
        }

        // 3. 라벨 삭제 및 연결 일정 이동
        LabelDeleteResponse response = labelService.deleteLabel(
                userId,
                labelId
        );

        // 4. 공통 응답 형식으로 반환
        return ResponseEntity.ok(
                ApiResponse.success(
                        "B108_LABEL_DELETE_200",
                        "라벨이 삭제되었습니다.",
                        response
                )
        );
    }

    /**
     * B108-5: 라벨 순서 변경
     */
    @PatchMapping("/order")
    @Override
    public ResponseEntity<ApiResponse<LabelListResponse>> updateLabelOrder(
            @RequestBody LabelOrderUpdateRequest request
    ) {
        // 1. SecurityContext에서 현재 사용자 ID 추출
        Long userId = extractUserIdFromSecurityContext();

        // 2. 인증 정보가 없는 경우 인증 예외 처리
        if (userId == null) {
            throw new BusinessException(
                    AuthErrorCode.AUTH_401
            );
        }

        // 3. 라벨 순서 및 기본 라벨 변경
        LabelListResponse response =
                labelService.updateLabelOrder(
                        userId,
                        request
                );

        // 4. 공통 응답 형식으로 반환
        return ResponseEntity.ok(
                ApiResponse.success(
                        "B108_LABEL_ORDER_UPDATE_200",
                        "라벨 순서가 변경되었습니다.",
                        response
                )
        );
    }

    /**
     * SecurityContext에서 현재 인증된 사용자의 ID를 추출합니다.
     *
     * 회원과 비회원 모두 인증 토큰의 principal에 userId가
     * 저장되어 있다는 현재 인증 구조를 기준으로 처리합니다.
     *
     * @return 인증된 사용자 ID, 인증 정보가 없으면 null
     */
    private Long extractUserIdFromSecurityContext() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.getPrincipal() instanceof Long) {
            return (Long) authentication.getPrincipal();
        }

        return null;
    }
}