package com.tryna.domain.event.dto;

import com.tryna.domain.event.enums.DateSource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "C103 일정 파싱 응답 DTO")
public record EventParseResponse(

        @Schema(description = "임시 일정 ID", example = "tmp_75c44199-81f2...")
        String tempEventId,

        @Schema(description = "추출된 일정 제목", example = "데이터베이스 중간고사")
        String eventTitle,
        @Schema(description = "수정 버전", example = "1")
        Integer draftRevision,

        @Schema(description = "시작 날짜", example = "2026-10-20")
        String startDate,

        @Schema(description = "날짜 파싱 출처")
        DateSource dateSource,

        @Schema(description = "종료 날짜", example = "2026-10-20")
        String endDate,

        @Schema(description = "시작 시간", example = "13:00")
        String startTime,

        @Schema(description = "종료 시간", example = "15:00")
        String endTime,

        @Schema(description = "추출된 장소 후보", example = "가천관 414호")
        String placeCandidate,

        @Schema(description = "(Legacy) 임베딩 벡터용 형태소 리스트")
        @Deprecated
        List<String> toEmbedding,

        @Schema(description = "종일 일정 여부 추론 결과", example = "false")
        Boolean isAllDayCandidate,

        @Schema(description = "유저 확인 필요 여부", example = "false")
        Boolean needsConfirmation,

        @Schema(description = "파싱 경고 목록")
        List<Warning> warnings,

        @Schema(description = "AI가 분석한 M1 Activity 코드", example = "TAKE_EXAM")
        String activityCode,

        @Schema(description = "AI가 분석한 M1 Entity 리스트")
        List<EntityDto> entities
) {
    public record Warning(
            String code,
            String message
    ) {}

    @Schema(description = "파싱된 개체 정보")
    public record EntityDto(
            @Schema(description = "개체명", example = "데이터베이스")
            String name,

            @Schema(description = "개체 역할", example = "SUBJECT")
            String role
    ) {}
}
