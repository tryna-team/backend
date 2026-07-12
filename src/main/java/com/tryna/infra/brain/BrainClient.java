package com.tryna.infra.brain;

import com.tryna.global.config.BrainClientProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class BrainClient {

    public static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final RestTemplate restTemplate;
    private final BrainClientProperties properties;

    public String getBaseUrl() {
        return properties.getBaseUrl();
    }

    public HttpHeaders createInternalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_API_KEY_HEADER, properties.getApiKey());
        return headers;
    }

    public <T> ResponseEntity<T> exchange(
            String path,
            HttpMethod method,
            HttpEntity<?> entity,
            Class<T> responseType
    ) {
        return restTemplate.exchange(
                properties.getBaseUrl() + path,
                method,
                entity,
                responseType
        );
    }
}
