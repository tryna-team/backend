package com.tryna.domain.event.controller.docs;

import com.tryna.domain.event.dto.EventCreateRequest;
import com.tryna.domain.event.dto.EventCreateResponse;
import com.tryna.domain.event.dto.EventDetailResponse;
import com.tryna.domain.event.dto.EventParseRequest;
import com.tryna.domain.event.dto.EventParseResponse;
import com.tryna.domain.event.dto.EventSearchResponse;
import com.tryna.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Events", description = "일정 생성/상세 관리 API")
public interface EventControllerDocs {

    @Operation(
            summary = "C103 일정 생성 미리보기",
            description = "사용자가 입력한 일정 원문을 분석하여 날짜, 시간, 장소, 임베딩 토큰 등 미리보기 후보값을 조회합니다.",
            operationId = "parseEvent"
    )
    @SecurityRequirement(name = "bearerAuth")
    ApiResponse<EventParseResponse> parseEvent(
            Authentication authentication,
            @RequestBody EventParseRequest request
    );

    @Operation(
            summary = "C104 일정 최종 저장",
            description = """
                    사용자가 확인한 일정 정보, 라벨, 선택한 준비/실행 항목을 최종 저장합니다.
                    
                    labelId는 선택 입력값입니다.
                    labelId가 전달되면 현재 사용자 소유의 삭제되지 않은 라벨인지 검증한 뒤 일정에 연결합니다.
                    labelId가 null이거나 생략되면 현재 사용자의 기본 라벨(isDefault=true)을 일정에 연결합니다.
                    응답의 labelId는 실제로 일정에 연결된 라벨 ID를 반환합니다.
                    """,
            operationId = "createEvent"
    )
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<EventCreateResponse>> createEvent(
            Authentication authentication,
            @RequestBody EventCreateRequest request
    );

    @Operation(
            summary = "B104 일정 상세 조회",
            description = "사용자에게 연결된 일정의 상세 정보를 조회합니다. 연결된 준비/실행 항목은 F103 API에서 조회합니다.",
            operationId = "getEventDetail"
    )
    @SecurityRequirement(name = "bearerAuth")
    ApiResponse<EventDetailResponse> getEventDetail(
            Authentication authentication,

            @Parameter(description = "조회할 일정 ID", required = true, example = "1")
            @PathVariable String eventId
    );

    @Operation(
            summary = "B107 키워드 검색",
            description = """
                    현재 사용자의 Tryna 내부 일정 제목과 저장된 준비/실행 항목 제목에서
                    입력한 키워드가 포함된 결과를 검색합니다.
                    
                    일정 제목이 일치하면 EVENT 유형으로 반환하고,
                    준비/실행 항목 제목이 일치하면 ACTION_ITEM 유형으로 반환합니다.
                    
                    일정 제목과 하위 준비/실행 항목이 모두 일치하면
                    EVENT 결과를 먼저 반환하고 해당 ACTION_ITEM 결과를 바로 다음에 반환합니다.
                    
                    준비/실행 항목만 일치한 경우 부모 일정은 별도의 EVENT 결과로 반환하지 않으며,
                    ACTION_ITEM 결과에 부모 일정 ID, 제목, 날짜 및 시간 정보를 포함합니다.
                    
                    삭제된 일정, 삭제된 준비/실행 항목, 외부 캘린더 일정,
                    저장 전 추천 후보 항목은 검색 대상에서 제외합니다.
                    
                    검색 결과가 없는 경우에도 200 OK와 빈 배열을 반환합니다.
                    """,
            operationId = "searchEvents"
    )
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<EventSearchResponse>> searchEvents(
            Authentication authentication,
            @Parameter(
                    description = "검색할 키워드",
                    required = true,
                    example = "여행"
            )
            @RequestParam("keyword") String keyword
    );
}
