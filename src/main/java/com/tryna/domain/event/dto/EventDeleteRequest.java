package com.tryna.domain.event.dto;

import com.tryna.domain.event.enums.DeleteScope;

public record EventDeleteRequest(
        DeleteScope deleteScope,
        Boolean cascade
) {
}
