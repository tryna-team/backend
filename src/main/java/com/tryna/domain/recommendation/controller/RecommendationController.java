package com.tryna.domain.recommendation.controller;

import com.tryna.domain.recommendation.controller.docs.RecommendationControllerDocs;
import com.tryna.domain.recommendation.dto.RecommendationDTO;
import com.tryna.domain.recommendation.service.RecommendationService;
import com.tryna.global.exception.AuthErrorCode;
import com.tryna.global.exception.BusinessException;
import com.tryna.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/recommendations")
public class RecommendationController implements RecommendationControllerDocs {
    private final RecommendationService recommendationService;

    @Override
    @PostMapping
    public ApiResponse<RecommendationDTO.RecommendationResDTO> getRecommendActionItems(
            Authentication authentication,
            RecommendationDTO.RecommendationReqDTO request)
    {
        Long userId = extractUserId(authentication);
        return ApiResponse.success(
                "D100_RECOMMENDATION_200",
                "실행 항목 추천 파이프라인 호출에 성공하였습니다.",
                recommendationService.recommend(request)
        );
    }

    private Long extractUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(AuthErrorCode.AUTH_401);
        }

        return userId;
    }
}
