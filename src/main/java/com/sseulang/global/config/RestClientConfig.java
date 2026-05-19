package com.sseulang.global.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

/**
 * 외부 HTTP 호출용 RestClient 공통 설정. timeout 명시 — 외부 API 가 hang 되면
 * thread 점유로 인한 cascading failure 방지.
 */
@Configuration
public class RestClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public RestClientCustomizer restClientCustomizer() {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
            factory.setReadTimeout((int) READ_TIMEOUT.toMillis());
            builder.requestFactory(factory);
        };
    }
}
