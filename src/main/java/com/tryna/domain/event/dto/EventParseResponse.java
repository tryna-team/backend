package com.tryna.domain.event.dto;

import com.tryna.domain.event.enums.DateSource;
import java.util.List;

public record EventParseResponse(
        String tempEventId,
        String eventTitle,
        String startDate,
        DateSource dateSource,
        String endDate,
        String startTime,
        String endTime,
        String placeCandidate,
        List<String> toEmbedding,
        Boolean isAllDayCandidate,
        Boolean needsConfirmation,
        List<Warning> warnings
) {

    public record Warning(
            String code,
            String message
    ) {
    }
}