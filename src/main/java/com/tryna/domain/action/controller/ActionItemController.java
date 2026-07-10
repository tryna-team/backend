package com.tryna.domain.action.controller;

import com.tryna.domain.action.controller.docs.ActionItemControllerDocs;
import com.tryna.domain.action.dto.ActionItemSaveRequest;
import com.tryna.domain.action.dto.ActionItemSaveResponse;
import com.tryna.domain.action.dto.ActionItemStatusUpdateRequest;
import com.tryna.domain.action.dto.ActionItemStatusUpdateResponse;
import com.tryna.domain.action.service.ActionItemService;
import com.tryna.global.exception.AuthErrorCode;
import com.tryna.global.exception.BusinessException;
import com.tryna.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ActionItemController implements ActionItemControllerDocs {

    private final ActionItemService actionItemService;

    /**
     * E105: 준비/실행 항목 일괄 저장
     */
    @PostMapping("/events/{eventId}/action-items")
    @Override
    public ResponseEntity<ApiResponse<ActionItemSaveResponse>> saveActionItems(
            @PathVariable("eventId") Long eventId,
            @Valid @RequestBody ActionItemSaveRequest request
    ) {
        // 1. SecurityContext에서 현재 사용자 ID 추출
        Long userId = extractUserIdFromSecurityContext();

        // 2. 인증 정보가 없는 경우 인증 예외 처리
        if (userId == null) {
            throw new BusinessException(AuthErrorCode.AUTH_401);
        }

        // 3. 준비/실행 항목과 피드백 로그 저장
        ActionItemSaveResponse response =
                actionItemService.saveActionItems(userId, eventId, request);

        // 4. 공통 응답 형식으로 반환
        return ResponseEntity.ok(
                ApiResponse.success(
                        "E105_ACTION_ITEM_200",
                        "준비/실행 항목 저장에 성공했습니다.",
                        response
                )
        );
    }

    /**
     * E106: 준비/실행 항목 완료 상태 변경
     */
    @PatchMapping("/action-items/{actionItemId}/status")
    @Override
    public ResponseEntity<ApiResponse<ActionItemStatusUpdateResponse>> updateActionItemStatus(
            @PathVariable("actionItemId") Long actionItemId,
            @Valid @RequestBody ActionItemStatusUpdateRequest request
    ) {
        // 1. SecurityContext에서 현재 사용자 ID 추출
        Long userId = extractUserIdFromSecurityContext();

        // 2. 인증 정보가 없는 경우 인증 예외 처리
        if (userId == null) {
            throw new BusinessException(
                    AuthErrorCode.AUTH_401
            );
        }

        // 3. 준비/실행 항목 상태 변경
        ActionItemStatusUpdateResponse response =
                actionItemService.updateActionItemStatus(
                        userId,
                        actionItemId,
                        request
                );

        // 4. 공통 응답 형식으로 반환
        return ResponseEntity.ok(
                ApiResponse.success(
                        "E106_ACTION_ITEM_200",
                        "준비/실행 항목 상태 변경에 성공했습니다.",
                        response
                )
        );
    }

    /**
     * SecurityContext에서 현재 인증된 사용자의 ID를 추출합니다.
     *
     * 회원과 비회원 모두 인증 토큰의 principal에 userId가 저장되어 있다는
     * 현재 인증 구조를 기준으로 처리합니다.
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