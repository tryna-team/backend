package com.tryna.domain.alarm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "F100 FCM 푸시 토큰 등록 요청 DTO")
public record AlarmPushTokenRequest(

        @Schema(description = "기기가 발급받은 FCM 푸시 토큰", example = "cxTayi6rhb21gdLO_UhIPJ:APA91bGkht_Aa4htHg...")
        @NotBlank(message = "fcmPushToken은 필수입니다.")
        String fcmPushToken

) {
}
