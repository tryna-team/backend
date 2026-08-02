package com.tryna.domain.label.controller.docs;

import com.tryna.domain.label.dto.LabelCreateRequest;
import com.tryna.domain.label.dto.LabelListResponse;
import com.tryna.domain.label.dto.LabelResponse;
import com.tryna.domain.label.dto.LabelUpdateRequest;
import com.tryna.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(
        name = "Labels",
        description = "라벨 관리 API"
)
public interface LabelControllerDocs {
    @Operation(
            summary = "B108-1 라벨 목록 조회",
            description = """
                현재 사용자의 활성 라벨 목록을 조회합니다.

                기본 라벨, 사용자 라벨, 외부 캘린더 라벨을 모두 반환하며,
                라벨은 sortOrder 오름차순으로 정렬됩니다.

                삭제된 라벨은 조회 결과에서 제외됩니다.
                """,
            operationId = "getLabels"
    )
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<LabelListResponse>> getLabels();

    @Operation(
            summary = "B108-2 라벨 생성",
            description = """
                    사용자 라벨을 생성합니다.
                    
                    라벨 이름은 필수이며 동일 사용자 안에서
                    정규화한 이름이 같은 활성 라벨은 중복 생성할 수 없습니다.
                    
                    색상을 생략하면 서버 기본 색상을 적용합니다.
                    """,
            operationId = "createLabel"
    )
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<LabelResponse>> createLabel(
            @RequestBody LabelCreateRequest request
    );

    @Operation(
            summary = "B108-3 라벨 수정",
            description = """
                현재 사용자가 소유한 라벨의 이름, 색상,
                표시 여부와 정렬 순서를 수정합니다.

                기본 라벨과 외부 캘린더 라벨도 이름과 색상을
                수정할 수 있습니다.

                외부 캘린더 라벨을 수정해도 외부 제공자의
                원본 캘린더 정보는 변경하지 않습니다.
                """,
            operationId = "updateLabel"
    )
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<LabelResponse>> updateLabel(
            @Parameter(
                    description = "수정할 라벨 ID",
                    required = true
            )
            @PathVariable("labelId") Long labelId,

            @RequestBody LabelUpdateRequest request
    );
}