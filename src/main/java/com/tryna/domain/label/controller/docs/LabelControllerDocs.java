package com.tryna.domain.label.controller.docs;

import com.tryna.domain.label.dto.LabelCreateRequest;
import com.tryna.domain.label.dto.LabelResponse;
import com.tryna.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(
        name = "Labels",
        description = "라벨 관리 API"
)
public interface LabelControllerDocs {

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
}