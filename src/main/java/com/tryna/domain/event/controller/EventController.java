package com.tryna.domain.event.controller;

import com.tryna.domain.event.controller.docs.EventControllerDocs;
import com.tryna.domain.event.dto.*;
import com.tryna.domain.event.service.EventCommandService;
import com.tryna.domain.event.service.EventParseService;
import com.tryna.domain.event.service.EventQueryService;
import com.tryna.global.exception.AuthErrorCode;
import com.tryna.global.exception.BusinessException;
import com.tryna.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/events")
public class EventController implements EventControllerDocs {

    private final EventCommandService eventCommandService;
    private final EventQueryService eventQueryService;
    private final EventParseService eventParseService;

    @PostMapping("/parse")
    public ApiResponse<EventParseResponse> parseEvent(
            Authentication authentication,
            @RequestBody EventParseRequest request
    ) {
        extractUserId(authentication);
        EventParseResponse response = eventParseService.parseEvent(request);
        return ApiResponse.success(
                "C103_EVENT_PREVIEW_200",
                "일정 생성 미리보기 후보 조회에 성공했습니다.",
                response
        );
    }

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

    /**
     * B107: 키워드 검색
     */
    @Override
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<EventSearchResponse>> searchEvents(
            Authentication authentication,
            @RequestParam("keyword") String keyword
    ) {
        // 1. Authentication에서 현재 사용자 ID 추출
        Long userId = extractUserId(authentication);

        // 2. 일정 및 준비/실행 항목 키워드 검색
        EventSearchResponse response =
                eventQueryService.searchEvents(userId, keyword);

        // 3. 공통 응답 형식으로 반환
        return ResponseEntity.ok(
                ApiResponse.success(
                        "B107_EVENT_SEARCH_200",
                        "검색 결과 조회에 성공했습니다.",
                        response
                )
        );
    }
}
