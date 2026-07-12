package com.tryna.domain.user.controller;

import com.tryna.domain.user.controller.docs.GuestControllerDocs;
import com.tryna.domain.user.dto.GuestCreateRequest;
import com.tryna.domain.user.dto.GuestCreateResponse;
import com.tryna.domain.user.service.UserService;
import com.tryna.domain.user.service.UserService.GuestResult;
import com.tryna.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/guests")
@RequiredArgsConstructor
public class GuestController implements GuestControllerDocs {

    private final UserService userService;

    @PostMapping
    @Override
    public ResponseEntity<ApiResponse<GuestCreateResponse>> createGuest(
            @Valid @RequestBody GuestCreateRequest request
    ) {
        GuestResult result = userService.createOrLoginGuest(request);

        if (result.isNew()) {
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.success("A102_GUEST_CREATE_201", "비회원 사용자 생성에 성공했습니다.", result.response()));
        } else {
            return ResponseEntity
                    .ok(ApiResponse.success("A102_GUEST_CREATE_200", "비회원 재접속에 성공했습니다.", result.response()));
        }
    }
}
