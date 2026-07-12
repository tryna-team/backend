package com.tryna.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        // 향후 타임아웃(Timeout) 설정이나 로깅 인터셉터가 필요해지면
        // 이 내부에서 커스터마이징을 진행하면 됩니다.
        return new RestTemplate();
    }
}