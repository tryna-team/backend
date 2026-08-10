package com.tryna.domain.event.dto;

import java.time.LocalDate;

public record EventParseRequest(
        String eventTitle,
        Integer draftRevision,
        LocalDate selectedDate
) {
}

