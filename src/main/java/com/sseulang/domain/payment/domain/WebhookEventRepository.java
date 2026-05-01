package com.sseulang.domain.payment.domain;

import java.util.Optional;

public interface WebhookEventRepository {

    /**
     * 저장. UNIQUE(source, eventId) 충돌 시 DataIntegrityViolationException 던짐 — 호출자가
     * findBy 로 기존 row 조회 후 멱등 응답 처리.
     */
    WebhookEvent save(WebhookEvent event);

    Optional<WebhookEvent> findBySourceAndEventId(WebhookEventSource source, String eventId);
}
