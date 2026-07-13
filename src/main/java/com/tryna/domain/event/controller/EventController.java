package com.tryna.domain.event.controller;

import com.tryna.domain.event.dto.EventCreateRequest;
import com.tryna.domain.event.dto.EventCreateResponse;
import com.tryna.domain.event.dto.EventDetailResponse;
import com.tryna.domain.event.service.EventCommandService;
import com.tryna.domain.event.service.EventQueryService;
import com.tryna.global.exception.AuthErrorCode;
import com.tryna.global.exception.BusinessException;
import com.tryna.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventCommandService eventCommandService;
    private final EventQueryService eventQueryService;

    @PostMapping
    public ResponseEntity<ApiResponse<EventCreateResponse>> createEvent(
            Authentication authentication,
            @RequestBody EventCreateRequest request
    ) {
        Long userId = extractUserId(authentication);
        EventCreateResponse response = eventCommandService.createEvent(userId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "C104_EVENT_SAVE_201",
                        "일정 최종 저장에 성공했습니다.",
                        response
                ));
    }

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
