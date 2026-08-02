package com.tryna.domain.label.dto;

import com.tryna.domain.label.entity.Labels;

import java.util.List;

public record LabelListResponse(
        List<LabelResponse> labels
) {

    /**
     * 조회된 라벨 엔티티 목록을 응답 DTO로 변환합니다.
     *
     * @param labels 현재 사용자의 활성 라벨 목록
     * @return 라벨 목록 응답
     */
    public static LabelListResponse from(
            List<Labels> labels
    ) {
        List<LabelResponse> responses = labels.stream()
                .map(LabelResponse::from)
                .toList();

        return new LabelListResponse(responses);
    }
}