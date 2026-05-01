package com.sseulang.global.infra.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 토스페이먼츠 API 자격증명. 운영은 환경변수 주입 필수, local 은 테스트 키 사용.
 */
@ConfigurationProperties(prefix = "app.toss")
public record TossProperties(
        String clientKey,
        String secretKey,
        String baseUrl,
        /**
         * Webhook HMAC 검증용 시크릿. 토스 콘솔에서 별도 발급 (보통 secretKey 와 다름).
         * 미설정 시 webhook 시그니처 검증 비활성 — local/dev 테스트 우회용. prod 는 반드시 주입.
         */
        String webhookSecret,
        /**
         * Webhook timestamp 의 허용 시간차 (초). 초과 시 replay 로 간주해 거부.
         * 기본 300 = 5분. 0 또는 음수면 timestamp 검증 비활성.
         */
        Long webhookReplayToleranceSeconds
) {
    public TossProperties {
        baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "https://api.tosspayments.com" : baseUrl;
        webhookReplayToleranceSeconds = (webhookReplayToleranceSeconds == null) ? 300L : webhookReplayToleranceSeconds;
    }
}
