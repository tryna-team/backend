package com.tryna.domain.user.dto;

import com.tryna.domain.user.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "앱 진입 상태 조회 응답 DTO")
public record UserStatusResponse(
        @Schema(description = "시스템 사용자 권한 상태", example = "NONE")
        UserRole userRole,

        @Schema(description = "사용자의 등록된 일정 존재 여부 (초기 캘린더 연동 제안 및 Empty State 분기용)", example = "false")
        boolean hasEvents,

        @Schema(description = "외부 캘린더 연동 여부 (연동 제안 중복 노출 방지용)", example = "false")
        boolean hasExternalCalendarConnection
) {
}
