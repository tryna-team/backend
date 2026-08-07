package com.tryna.domain.auth.dto;

import com.tryna.domain.auth.enums.Provider;
import com.tryna.domain.term.enums.TermType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "A105 소셜 로그인 및 회원가입 요청 DTO (인가 코드 방식)")
public record AuthSessionCreateRequest(

        @Schema(description = "외부 연동/로그인 제공자", example = "KAKAO")
        @NotNull(message = "제공자(Provider)는 필수입니다.")
        Provider provider,

        @Schema(description = "소셜 서버에서 발급받은 인가 코드 (Authorization Code)", example = "4/0AeaY...")
        @NotBlank(message = "인가 코드(Authorization Code)는 필수입니다.")
        String authorizationCode,

        @Schema(description = "구글 콘솔에 등록된 승인된 리디렉션 URI (웹 환경 필수, 모바일/테스트 시 생략 가능)", example = "http://localhost:3000")
        String redirectUri,

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