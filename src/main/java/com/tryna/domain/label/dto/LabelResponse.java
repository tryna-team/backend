package com.tryna.domain.label.dto;

import com.tryna.domain.label.entity.Labels;
import com.tryna.domain.label.enums.LabelType;

public record LabelResponse(
        Long labelId,
        Long externalCalendarId,
        String name,
        LabelType labelType,
        String color,
        Boolean isDefault,
        Boolean isVisible,
        Integer sortOrder
) {

    public static LabelResponse from(Labels label) {
        Long externalCalendarId = label.getExternalCalendar() == null
                ? null
                : label.getExternalCalendar().getExternalCalendarId();

        return new LabelResponse(
                label.getLabelId(),
                externalCalendarId,
                label.getName(),
                label.getLabelType(),
                label.getColor(),
                label.getIsDefault(),
                label.getIsVisible(),
                label.getSortOrder()
        );
    }
}