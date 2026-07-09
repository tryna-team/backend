package com.tryna.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 필요 여부 확인 응답 DTO")
public record PermissionCheckResponse(
        @Schema(description = "해당 기능 수행을 위해 정식 회원(USER) 로그인이 필요한지 여부", example = "true")
        boolean isLoginRequired,

        @Schema(description = "앱의 바텀시트 등에 표시할 로그인 필요 안내 및 이점 문구",
                example = "이 기능을 사용하려면 로그인이 필요해요.\n\n로그인하면 일정과 준비 항목을 안전하게 저장하고,\n다른 기기에서도 이어서 확인할 수 있어요.")
        String guideMessage
) {
}
