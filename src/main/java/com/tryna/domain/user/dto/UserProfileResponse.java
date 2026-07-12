package com.tryna.domain.user.dto;

import java.util.List;

public record UserProfileResponse(
        Long userId,
        String userRole,
        String nickname,
        String createdAt,
        List<LinkedAuthDto> linkedAuths,
        boolean hasExternalCalendarConnection
) {
    public record LinkedAuthDto(
            String provider,
            String email
    ) {}
}