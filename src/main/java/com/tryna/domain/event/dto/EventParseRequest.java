package com.tryna.domain.event.dto;

import java.time.LocalDate;

public record EventParseRequest(
        String eventTitle,
        LocalDate selectedDate
) {
}
