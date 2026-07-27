package com.tryna.domain.action.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ItemType {
    TIMED_ACTION,
    UNTIMED_PREP,
    UNRESOLVED;

    @JsonValue
    public String toValue() {
        return name().toLowerCase();
    }
}
