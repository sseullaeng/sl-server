package com.sseulang.domain.payment.domain;

/** Webhook 발신 PG 식별. {@code webhook_events.source} 컬럼 매핑. */
public enum WebhookEventSource {
    TOSS,
    KAKAO
}
