package com.tryna.domain.recommendation.controller.docs;

import com.tryna.domain.recommendation.dto.RecommendationDTO;
import com.tryna.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Recommendations", description = "실행 항목 추천 API")
public interface RecommendationControllerDocs {

    @Operation(
            summary = "실행 항목 추천 API",
            description = "brain 서버의 실행 항목 추천 파이프라인을 호출하는 API입니다.",
            operationId = "recommendActionItems"
    )
    @SecurityRequirement(name = "bearerAuth")
    ApiResponse<RecommendationDTO.RecommendationResDTO> getRecommendActionItems(
            Authentication authentication,
            @Valid @RequestBody RecommendationDTO.RecommendationReqDTO request
            );
}
