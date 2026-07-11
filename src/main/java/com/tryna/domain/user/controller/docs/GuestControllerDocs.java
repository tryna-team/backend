package com.tryna.domain.user.controller.docs;

import com.tryna.domain.user.dto.GuestCreateRequest;
import com.tryna.domain.user.dto.GuestCreateResponse;
import com.tryna.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Guests", description = "비회원 사용자 API")
public interface GuestControllerDocs {

    @Operation(
            summary = "A102 비회원 시작",
            description = "임시 사용자를 생성하거나 기존 비회원으로 재접속합니다.",
            operationId = "createGuest"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "비회원 사용자 생성에 성공했습니다. (A102_GUEST_CREATE_201)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "비회원 재접속에 성공했습니다. (A102_GUEST_CREATE_200)"
            )
    })
    ResponseEntity<ApiResponse<GuestCreateResponse>> createGuest(
            @Valid @RequestBody GuestCreateRequest request
    );
}
