package com.tryna.domain.label.controller.docs;

import com.tryna.domain.label.dto.*;
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
                    
                    color는 서버에서 허용한 6개 라벨 색상 중 하나만 사용할 수 있으며,
                    생략하면 기본 연두색을 적용합니다.
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

                color는 서버에서 허용한 6개 라벨 색상 중 하나만 사용할 수 있습니다
            
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

    @Operation(
            summary = "B108-4 라벨 삭제",
            description = """
                현재 사용자가 소유한 사용자 라벨을 삭제합니다.

                삭제된 라벨에 연결된 일정은 삭제하지 않고
                사용자의 기본 라벨로 이동합니다.

                외부 캘린더 라벨은
                이 API로 삭제할 수 없습니다.
                """,
            operationId = "deleteLabel"
    )
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<LabelDeleteResponse>> deleteLabel(
            @Parameter(
                    description = "삭제할 라벨 ID",
                    required = true
            )
            @PathVariable("labelId") Long labelId
    );

    @Operation(
            summary = "B108-5 라벨 순서 변경",
            description = """
                현재 사용자가 소유한 활성 사용자 라벨의 표시 순서를 변경합니다.

                변경 후 최종 순서대로 정렬한 전체 사용자 라벨 ID를 전달합니다.
                요청 배열 순서대로 sortOrder를 1부터 저장하고,
                첫 번째 라벨을 새로운 기본 라벨로 지정합니다.

                외부 캘린더 라벨은 순서 변경 대상에 포함하지 않습니다.
                """,
            operationId = "updateLabelOrder"
    )
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<LabelListResponse>> updateLabelOrder(
            @RequestBody LabelOrderUpdateRequest request
    );
}