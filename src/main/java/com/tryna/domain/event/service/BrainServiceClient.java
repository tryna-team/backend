package com.tryna.domain.event.service;

import com.tryna.domain.event.dto.EventGraphSyncRequest;
import java.util.concurrent.CompletableFuture;

import com.tryna.infra.brain.BrainClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BrainServiceClient {

    private final BrainClient brainClient;

    public void requestGraphSync(EventGraphSyncRequest request) {
        CompletableFuture.runAsync(() -> {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                brainClient.exchange(
                        "/api/v1/graph/sync",
                        HttpMethod.POST,
                        new HttpEntity<>(request, headers),
                        Void.class
                );
                log.info("Brain 서버로 그래프 동기화 요청 성공: eventId={}", request.eventId());
            } catch (Exception e) {
                log.error("Brain 서버 그래프 동기화 요청 실패: eventId={}, error={}", request.eventId(), e.getMessage());
            }
        });
    }
}