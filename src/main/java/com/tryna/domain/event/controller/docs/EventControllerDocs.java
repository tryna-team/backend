package com.tryna.domain.event.controller.docs;

import com.tryna.domain.event.dto.EventCreateRequest;
import com.tryna.domain.event.dto.EventCreateResponse;
import com.tryna.domain.event.dto.EventDeleteRequest;
import com.tryna.domain.event.dto.EventDeleteResponse;
import com.tryna.domain.event.dto.EventDetailResponse;
import com.tryna.domain.event.dto.EventParseRequest;
import com.tryna.domain.event.dto.EventParseResponse;
import com.tryna.domain.event.dto.EventSearchResponse;
import com.tryna.domain.event.dto.EventUpdateRequest;
import com.tryna.domain.event.dto.EventUpdateResponse;
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
            summary = "C106 일정 삭제",
            description = """
                    저장된 Tryna 내부 일정을 삭제합니다.

                    삭제 대상은 현재 사용자가 OWNER로 연결된 내부 일정이어야 합니다.
                    외부 캘린더 원본 일정은 삭제하지 않습니다.
                    cascade는 MVP에서 true만 허용하며, 일정에 연결된 준비/실행 항목도 함께 soft delete 처리합니다.
                    일반 일정은 deleteScope=SINGLE로 요청합니다.
                    반복 일정의 특정 회차만 삭제할 때는 deleteScope=SINGLE과 occurrenceDate를 함께 전달합니다.
                    반복 일정의 선택 회차 및 이후 회차를 삭제할 때는 deleteScope=THIS_AND_FUTURE와 occurrenceDate를 함께 전달합니다.
                    """,
            operationId = "deleteEvent"
    )
    @SecurityRequirement(name = "bearerAuth")
    ApiResponse<EventDeleteResponse> deleteEvent(
            Authentication authentication,
            @Parameter(description = "삭제할 일정 ID", required = true, example = "1")
            @PathVariable Long eventId,
            @RequestBody EventDeleteRequest request
    );

    @Operation(
            summary = "C107 일정 수정",
            description = """
                    저장된 Tryna 내부 일정을 수정합니다.
                    
                    일반 일정은 updateScope=SINGLE로 수정합니다.
                    반복 일정의 특정 회차만 수정할 때는 updateScope=SINGLE과 occurrenceDate를 함께 전달합니다.
                    이 경우 선택 회차는 기존 반복 일정에서 제외하고, 수정된 단일 일정으로 새로 저장합니다.
                    반복 일정의 선택 회차 및 이후 회차를 수정할 때는 updateScope=THIS_AND_FUTURE와 occurrenceDate를 함께 전달합니다.
                    이 경우 기존 반복 일정은 선택 회차 전날까지로 종료하고, 수정된 반복 일정을 새로 저장합니다.
                    
                    수정 대상은 현재 사용자가 OWNER로 연결된 내부 일정이어야 합니다.
                    외부 캘린더 원본 일정은 수정하지 않습니다.
                    
                    labelId는 선택 입력값입니다.
                    labelId가 전달되면 현재 사용자 소유의 삭제되지 않은 라벨인지 검증한 뒤 일정에 연결합니다.
                    labelId가 null이거나 생략되면 기존 일정에 연결된 라벨을 유지합니다.
                    응답의 labelId는 실제로 일정에 연결된 라벨 ID를 반환합니다.
                    
                    일정 날짜가 변경되면 offsetDays가 있는 시간형 실행 항목의 displayDate를
                    새 시작일 기준으로 함께 보정합니다.
                    날짜를 제거해 보정할 수 없는 경우 requiresActionItemReview=true를 반환합니다.
                    반복 일정 회차를 새 일정으로 분리하는 경우 기존 준비/실행 항목은 새 일정에도 복사합니다.
                    
                    준비/실행 항목의 내용 수정, 삭제, 직접 추가, 완료 처리는 본 API가 담당하지 않습니다.
                    수정 화면에서 준비/실행 항목 변경이 발생한 경우 Action Items API를 별도로 호출합니다.
                    본 API는 일정 정보 수정에 따른 기존 시간형 실행 항목 날짜 보정까지만 처리합니다.
                    """,
            operationId = "updateEvent"
    )
    @SecurityRequirement(name = "bearerAuth")
    ApiResponse<EventUpdateResponse> updateEvent(
            Authentication authentication,
            @Parameter(description = "수정할 일정 ID", required = true, example = "1")
            @PathVariable Long eventId,
            @RequestBody EventUpdateRequest request
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
