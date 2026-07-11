package com.tryna.domain.event.controller;

import com.tryna.domain.event.dto.EventDetailResponse;
import com.tryna.domain.event.service.EventQueryService;
import com.tryna.global.exception.AuthErrorCode;
import com.tryna.global.exception.BusinessException;
import com.tryna.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventQueryService eventQueryService;

    @GetMapping("/{eventId}")
    public ApiResponse<EventDetailResponse> getEventDetail(
            Authentication authentication,
            @PathVariable String eventId
    ) {
        Long userId = extractUserId(authentication);
        EventDetailResponse response = eventQueryService.getEventDetail(userId, eventId);
        return ApiResponse.success(
                "B104_EVENT_DETAIL_200",
                "일정 상세 조회에 성공했습니다.",
                response
        );
    }

    private Long extractUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(AuthErrorCode.AUTH_401);
        }

        return userId;
    }
}
