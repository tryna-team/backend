package com.tryna.domain.label.dto;

import com.tryna.domain.label.enums.LabelColor;

public record LabelUpdateRequest(
        String name,
        LabelColor color,
        Boolean isVisible
) {

    public boolean hasNoChanges() {
        return name == null
                && color == null
                && isVisible == null;
    }
}