package com.tryna.domain.event.controller.docs;

import com.tryna.domain.event.dto.EventSearchResponse;
import com.tryna.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Events", description = "일정 관리 API")
public interface EventControllerDocs {

    @Operation(
            summary = "B107 키워드 검색",
            description = "현재 사용자의 Tryna 내부 일정 제목과 저장된 준비/실행 항목 제목에서 키워드를 검색합니다.",
            operationId = "searchEvents"
    )
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<EventSearchResponse>> searchEvents(
            Authentication authentication,
            @Parameter(
                    description = "검색할 키워드",
                    required = true,
                    example = "고기"
            )
            @RequestParam("keyword") String keyword
    );

}
