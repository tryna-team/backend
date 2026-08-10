package com.tryna.domain.event.service;

import com.tryna.domain.event.dto.EventParseRequest;
import com.tryna.domain.event.dto.EventParseResponse;
import com.tryna.global.exception.BusinessException;
import com.tryna.global.exception.EventErrorCode;
import com.tryna.infra.brain.BrainClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;

@Service
@Slf4j
@RequiredArgsConstructor
public class EventParseService {

    private static final String EVENT_PREVIEW_PATH = "/api/v1/event-previews";

    private final BrainClient brainClient;

    public EventParseResponse parseEvent(EventParseRequest request) {
        validateEventTitle(request);
        EventParseRequest canonicalRequest = canonicalizeRequest(request);

        try {
            ResponseEntity<EventParseResponse> response = brainClient.exchange(
                    EVENT_PREVIEW_PATH,
                    HttpMethod.POST,
                    createRequestEntity(canonicalRequest),
                    EventParseResponse.class
            );

            EventParseResponse body = response.getBody();
            if (body == null) {
                throw new BusinessException(EventErrorCode.C102_EVENT_PARSE_500);
            }

            return withTempEventId(canonicalRequest, body);
        } catch (RestClientException e) {
            log.warn("Brain event preview API request failed. path={}", EVENT_PREVIEW_PATH, e);
            throw new BusinessException(EventErrorCode.C102_EVENT_PARSE_500);
        }
    }

    private EventParseResponse withTempEventId(EventParseRequest request, EventParseResponse response) {
        validateResponseDraftRevision(request, response);
        validateResponseTempEventId(request, response);
        return response;
    }

    private EventParseRequest canonicalizeRequest(EventParseRequest request) {
        return new EventParseRequest(
                normalizeTempEventId(request.tempEventId()),
                request.eventTitle(),
                request.draftRevision(),
                request.selectedDate()
        );
    }

    private String normalizeTempEventId(String tempEventId) {
        if (!StringUtils.hasText(tempEventId)) {
            return null;
        }
        return tempEventId.trim();
    }

    private void validateResponseTempEventId(EventParseRequest request, EventParseResponse response) {
        if (!StringUtils.hasText(response.tempEventId())) {
            throw new BusinessException(EventErrorCode.C102_EVENT_PARSE_500);
        }

        if (StringUtils.hasText(request.tempEventId())
                && !response.tempEventId().equals(request.tempEventId())) {
            throw new BusinessException(EventErrorCode.C102_EVENT_PARSE_500);
        }
    }

    private void validateResponseDraftRevision(EventParseRequest request, EventParseResponse response) {
        if (response.draftRevision() == null || !response.draftRevision().equals(request.draftRevision())) {
            throw new BusinessException(EventErrorCode.C102_EVENT_PARSE_500);
        }
    }

    private void validateEventTitle(EventParseRequest request) {
        if (request == null || !StringUtils.hasText(request.eventTitle())) {
            throw new BusinessException(EventErrorCode.C101_EVENT_INPUT_400);
        }

        if (request.draftRevision() == null || request.draftRevision() < 0) {
            throw new BusinessException(EventErrorCode.C101_EVENT_INPUT_400);
        }
    }

    private HttpEntity<EventParseRequest> createRequestEntity(EventParseRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(request, headers);
    }
}
