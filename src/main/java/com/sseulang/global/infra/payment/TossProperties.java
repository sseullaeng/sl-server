package com.sseulang.global.infra.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 토스페이먼츠 API 자격증명. 운영은 환경변수 주입 필수, local 은 테스트 키 사용.
 */
@ConfigurationProperties(prefix = "app.toss")
public record TossProperties(
        String clientKey,
        String secretKey,
        String baseUrl
) {
    public TossProperties {
        baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "https://api.tosspayments.com" : baseUrl;
    }
}
