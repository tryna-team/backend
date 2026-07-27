package com.tryna.domain.recommendation.service;

import com.tryna.domain.recommendation.dto.RecommendationDTO;
import com.tryna.domain.recommendation.enums.SuggestionStatus;
import com.tryna.infra.brain.BrainClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Objects;


@Service
@Slf4j
@RequiredArgsConstructor
public class RecommendationService {

    private static final String RECOMMENDATION_PATH =
            "/api/v1/recommendations";

    private final BrainClient brainClient;

    public RecommendationDTO.RecommendationResDTO recommend(
            RecommendationDTO.RecommendationReqDTO request
    ) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<RecommendationDTO.RecommendationResDTO> response =
                    brainClient.exchange(
                            RECOMMENDATION_PATH,
                            HttpMethod.POST,
                            new HttpEntity<>(request, headers),
                            RecommendationDTO.RecommendationResDTO.class
                    );

            RecommendationDTO.RecommendationResDTO body = response.getBody();

            if (body == null) {
                return fallbackResponse(
                        request,
                        "추천 결과를 생성하지 못했습니다."
                );
            }

            if (!Objects.equals(request.tempEventId(), body.tempEventId())
                    || !Objects.equals(request.draftRevision(), body.draftRevision())
            ) {
                log.warn(
                        "Brain recommendation request-response mismatch. " +
                                "requestedTempEventId={}, respondedTempEventId={}, " +
                                "requestedRevision={}, respondedRevision={}",
                        request.tempEventId(),
                        body.tempEventId(),
                        request.draftRevision(),
                        body.draftRevision()
                );

                return fallbackResponse(
                        request,
                        "추천 요청과 응답 정보가 일치하지 않습니다."
                );
            }

            return body;
        } catch (RestClientException e) {
            log.warn(
                    "Brain recommendation API request failed. tempEventId={}, draftRevision={}, path={}",
                    request.tempEventId(),
                    request.draftRevision(),
                    RECOMMENDATION_PATH,
                    e
            );

            return fallbackResponse(
                    request,
                    "추천 서버 호출에 실패했습니다."
            );
        }
    }

    // 파이프라인 호출 실패시 빈 결과 응답
    private RecommendationDTO.RecommendationResDTO fallbackResponse(
            RecommendationDTO.RecommendationReqDTO request,
            String errorMessage
    ) {
        return RecommendationDTO.RecommendationResDTO.builder()
                .tempEventId(request.tempEventId())
                .draftRevision(request.draftRevision())
                .suggestionStatus(SuggestionStatus.ERROR)
                .suggestions(List.of())
                .errors(List.of(errorMessage))
                .build();
    }
}
