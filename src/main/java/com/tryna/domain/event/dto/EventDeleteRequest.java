package com.tryna.domain.event.dto;

import com.tryna.domain.event.enums.DeleteScope;
import java.time.LocalDate;

public record EventDeleteRequest(
        DeleteScope deleteScope,
        Boolean cascade,
        LocalDate occurrenceDate
) {
}
