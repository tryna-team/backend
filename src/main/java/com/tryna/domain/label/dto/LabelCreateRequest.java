package com.tryna.domain.label.dto;

import com.tryna.domain.label.enums.LabelColor;

public record LabelCreateRequest(
        String name,
        LabelColor color
) {
}