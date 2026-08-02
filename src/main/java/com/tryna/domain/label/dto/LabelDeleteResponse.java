package com.tryna.domain.label.dto;

public record LabelDeleteResponse(
        Long deletedLabelId,
        Integer movedEventCount,
        Long destinationLabelId,
        Boolean defaultLabelChanged
) {

    public static LabelDeleteResponse of(
            Long deletedLabelId,
            Integer movedEventCount,
            Long destinationLabelId,
            Boolean defaultLabelChanged
    ) {
        return new LabelDeleteResponse(
                deletedLabelId,
                movedEventCount,
                destinationLabelId,
                defaultLabelChanged
        );
    }
}