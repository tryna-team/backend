package com.tryna.domain.label.dto;

public record LabelDeleteResponse(
        Long deletedLabelId,
        Integer movedEventCount,
        Long destinationLabelId
) {

    public static LabelDeleteResponse of(
            Long deletedLabelId,
            Integer movedEventCount,
            Long destinationLabelId
    ) {
        return new LabelDeleteResponse(
                deletedLabelId,
                movedEventCount,
                destinationLabelId
        );
    }
}