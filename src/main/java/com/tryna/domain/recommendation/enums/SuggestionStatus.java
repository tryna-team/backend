package com.tryna.domain.recommendation.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum SuggestionStatus {
    READY,
    EMPTY,
    ERROR;

    @JsonValue
    public String toValue() {
        return name().toLowerCase();
    }
}
