package com.tryna.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GoogleUserInfoResponse(
        @JsonProperty("id") String id,
        @JsonProperty("email") String email,
        @JsonProperty("name") String name,
        @JsonProperty("picture") String picture
) {}