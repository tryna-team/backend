package com.tryna.domain.alarm.dto;

import java.util.List;

public record AlarmListResponse(
        List<AlarmDetailResponse> alarms,
        PageInfo pageInfo
) {

    public static AlarmListResponse of(List<AlarmDetailResponse> alarms, PageInfo pageInfo) {
        return new AlarmListResponse(alarms, pageInfo);
    }

    public record PageInfo(
            boolean hasNext,
            String nextCursor
    ) {
    }
}
