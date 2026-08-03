package com.tryna.domain.label.dto;

import java.util.List;

public record LabelOrderUpdateRequest(
        List<Long> labelIds
) {
}