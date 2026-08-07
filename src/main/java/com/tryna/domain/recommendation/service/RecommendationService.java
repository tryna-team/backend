package com.tryna.domain.recommendation.service;

import com.tryna.domain.recommendation.dto.RecommendationDTO;
import com.tryna.domain.recommendation.enums.SuggestionStatus;
import com.tryna.infra.brain.BrainClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Objects;


@Service
@Slf4j
@RequiredArgsConstructor
public class RecommendationService {

    private static final String RECOMMENDATION_PATH =
            "/api/v1/recommendations";
    private static final String ERROR_RESPONSE_BODY_MISSING =
            "D100_BRAIN_RESPONSE_BODY_MISSING";
    private static final String ERROR_RESPONSE_MISMATCH =
            "D100_BRAIN_RESPONSE_MISMATCH";
    private static final String ERROR_CLIENT =
            "D100_BRAIN_CLIENT_ERROR";
    private static final String ERROR_SERVER =
            "D100_BRAIN_SERVER_ERROR";
    private static final String ERROR_CONNECTION =
            "D100_BRAIN_CONNECTION_FAILED";
    private static final String ERROR_INVALID_RESPONSE =
            "D100_BRAIN_INVALID_RESPONSE";

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
                        ERROR_RESPONSE_BODY_MISSING,
                        "추천 서버 응답 본문이 없습니다."
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
                        ERROR_RESPONSE_MISMATCH,
                        "추천 요청과 응답 정보가 일치하지 않습니다."
                );
            }

            return body;
        } catch (HttpClientErrorException e) {
            log.error(
                    "Brain recommendation client error. " +
                            "status={}, tempEventId={}, draftRevision={}, path={}",
                    e.getStatusCode(),
                    request.tempEventId(),
                    request.draftRevision(),
                    RECOMMENDATION_PATH,
                    e
            );

            return fallbackResponse(
                    request,
                    ERROR_CLIENT,
                    "추천 항목을 불러오지 못했습니다."
            );

        } catch (HttpServerErrorException e) {
            log.error(
                    "Brain recommendation server error. " +
                            "status={}, tempEventId={}, draftRevision={}, path={}",
                    e.getStatusCode(),
                    request.tempEventId(),
                    request.draftRevision(),
                    RECOMMENDATION_PATH,
                    e
            );

            return fallbackResponse(
                    request,
                    ERROR_SERVER,
                    "추천 항목을 불러오지 못했습니다."
            );

        } catch (ResourceAccessException e) {
            log.warn(
                    "Brain recommendation connection failed. " +
                            "tempEventId={}, draftRevision={}, path={}",
                    request.tempEventId(),
                    request.draftRevision(),
                    RECOMMENDATION_PATH,
                    e
            );

            return fallbackResponse(
                    request,
                    ERROR_CONNECTION,
                    "추천 서버에 연결하지 못했습니다."
            );

        } catch (RestClientException e) {
            log.error(
                    "Brain recommendation response handling failed. " +
                            "tempEventId={}, draftRevision={}, path={}",
                    request.tempEventId(),
                    request.draftRevision(),
                    RECOMMENDATION_PATH,
                    e
            );

            return fallbackResponse(
                    request,
                    ERROR_INVALID_RESPONSE,
                    "추천 서버 응답을 처리하지 못했습니다."
            );
        }
    }

    // Brain 추천 처리 실패 시 ERROR 상태의 대체 응답 생성
    private RecommendationDTO.RecommendationResDTO fallbackResponse(
            RecommendationDTO.RecommendationReqDTO request,
            String errorCode,
            String errorMessage
    ) {
        return RecommendationDTO.RecommendationResDTO.builder()
                .tempEventId(request.tempEventId())
                .draftRevision(request.draftRevision())
                .suggestionStatus(SuggestionStatus.ERROR)
                .suggestions(List.of())
                .errorCode(errorCode)
                .errors(List.of(errorMessage))
                .build();
    }
}
