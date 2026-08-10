package com.tryna.infra.brain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BrainErrorResponse(
        boolean success,
        String code,
        String message,
        Object data
) {
}
