package com.tryna.infra.brain;

import com.tryna.global.config.BrainClientProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class BrainClient {

    public static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final RestTemplate restTemplate;
    private final BrainClientProperties properties;

    public BrainClient(
            @Qualifier("brainRestTemplate") RestTemplate restTemplate,
            BrainClientProperties properties
    ) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

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
        HttpHeaders headers = createInternalHeaders();
        Object body = null;

        if (entity != null) {
            if (entity.getHeaders() != null) {
                entity.getHeaders().forEach((name, values) -> {
                    if (!INTERNAL_API_KEY_HEADER.equalsIgnoreCase(name)) {
                        headers.addAll(name, values);
                    }
                });
            }
            body = entity.getBody();
        }

        HttpEntity<?> requestEntity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(
                properties.getBaseUrl() + path,
                method,
                requestEntity,
                responseType
        );
    }
}
