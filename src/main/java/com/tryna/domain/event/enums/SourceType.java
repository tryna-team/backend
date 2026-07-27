package com.tryna.domain.event.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum SourceType {
    USER_NATURAL_LANGUAGE,
    USER_MANUAL_EDIT,
    EXTERNAL_CALENDAR,
    EXTERNAL_BASED_INTERNAL;

    @JsonValue
    public String toValue() {
        return name().toLowerCase();
    }
}
