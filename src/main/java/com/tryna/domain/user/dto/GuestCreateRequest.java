package com.tryna.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "비회원 생성 및 시작 요청 DTO")
public record GuestCreateRequest(
        @Schema(description = "비회원 기기 식별을 위한 임시 ID", example = "device-uuid-1234-5678")
        @NotBlank(message = "guestId는 필수값입니다.")
        String guestId,

        @Schema(description = "접속 기기 정보 (선택)", example = "iPhone14,2")
        String deviceInfo,

        @Schema(description = "기기별 푸시 알림 발송용 FCM 토큰 (선택)", example = "fcm-token-example")
        String fcmToken
) {
}
