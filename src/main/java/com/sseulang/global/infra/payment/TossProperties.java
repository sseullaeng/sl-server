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
        Long webhookReplayToleranceSeconds,
        /**
         * confirm 단의 토스 lookup 검증 우회 플래그.
         *
         * <p><b>운영 절대 사용 X</b> — 토스 시크릿 키 미발급 환경 (dev/QA) 에서 결제 흐름만 검증할 때 사용.
         * true 면 confirmCharge 가 토스 API 를 호출하지 않고 클라이언트 amount 그대로 markAsPaid + 잔액 적립.
         * 1차 amount 위변조 가드 (clientAmount == storedAmount) 는 그대로 유지.</p>
         *
         * <p>활성 시 부팅 로그에 WARN 1회 + confirm 호출마다 WARN 로그 출력 — 운영자가 켜진 상태를 즉시 인지.</p>
         */
        Boolean skipVerify
) {
    public TossProperties {
        baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "https://api.tosspayments.com" : baseUrl;
        webhookReplayToleranceSeconds = (webhookReplayToleranceSeconds == null) ? 300L : webhookReplayToleranceSeconds;
        skipVerify = (skipVerify != null) && skipVerify;
    }
}
