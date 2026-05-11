package com.sseulang.domain.payment.domain;

import java.util.Optional;

public interface WebhookEventRepository {

    

    WebhookEvent save(WebhookEvent event);

    Optional<WebhookEvent> findBySourceAndEventId(WebhookEventSource source, String eventId);
}
