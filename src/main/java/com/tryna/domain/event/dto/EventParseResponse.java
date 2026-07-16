package com.tryna.domain.event.dto;

import java.util.List;

public record EventParseResponse(
        String sourceText,
        String dateCandidate,
        String timeCandidate,
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
