package com.tryna.domain.auth.dto;

import com.tryna.domain.auth.enums.Provider;
import com.tryna.domain.term.enums.TermType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "A105 소셜 로그인 및 회원가입 요청 DTO")
public record AuthSessionCreateRequest(

        @Schema(description = "외부 연동/로그인 제공자", example = "KAKAO")
        @NotNull(message = "제공자(Provider)는 필수입니다.")
        Provider provider,

        @Schema(description = "소셜 서버에서 발급받은 인증 토큰", example = "kakao-123456789")
        @NotBlank(message = "OAuth 액세스 토큰은 필수입니다.")
        String oauthAccessToken,

        @Schema(description = "소셜 서버에서 발급받은 리프레시 토큰 (최초 로그인/권한 재동의 시에만 프론트가 전달)", example = "1//0eA...")
        @Size(max = 512, message = "OAuth 리프레시 토큰은 512자를 초과할 수 없습니다.") //
        String oauthRefreshToken,

        @Schema(description = "신규 가입 시 동의한 약관 유형 목록 (기존 회원은 빈 배열 가능)", example = "[\"SERVICE\", \"PRIVACY\", \"LOCATION\"]")
        @NotNull(message = "약관 동의 목록은 Null일 수 없습니다.")
        List<TermType> agreedTermTypes,

        @Schema(description = "기기 고유 식별자 (Redis 세션 키 생성용)", example = "device-uuid-1234-5678")
        @NotBlank(message = "기기 식별자(deviceId)는 필수입니다.")
        String deviceId,

        @Schema(description = "접속 기기 정보 (선택)", example = "iPhone14,2")
        String deviceInfo,

        @Schema(description = "기기별 푸시 알림 발송용 FCM 토큰 (선택)", example = "fcm-token-example")
        String fcmToken
) {
}