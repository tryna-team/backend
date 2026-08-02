package com.tryna.domain.label.dto;

public record LabelUpdateRequest(
        String name,
        String color,
        Boolean isVisible,
        Integer sortOrder
) {

    public boolean hasNoChanges() {
        return name == null
                && color == null
                && isVisible == null
                && sortOrder == null;
    }
}