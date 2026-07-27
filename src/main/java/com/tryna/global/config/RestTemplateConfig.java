package com.tryna.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        // 기본 Factory를 사용하여 타임아웃 설정
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        // 타임아웃을 밀리초(ms) 단위로 설정합니다. (5000ms = 5초)
        factory.setConnectTimeout(5000); // 구글 서버와 연결(Connect)을 기다리는 시간
        factory.setReadTimeout(5000);    // 데이터를 읽어오는(Read) 데까지 기다리는 시간

        return new RestTemplate(factory);
    }

    @Bean("brainRestTemplate")
    public RestTemplate brainRestTemplate() {
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(3000);
        factory.setReadTimeout(15000);

        return new RestTemplate(factory);
    }
}