package com.sseulang.domain.payment.infrastructure.persistence;

import com.sseulang.domain.payment.domain.WebhookEvent;
import com.sseulang.domain.payment.domain.WebhookEventSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface WebhookEventJpaRepository extends JpaRepository<WebhookEvent, Long> {
    Optional<WebhookEvent> findBySourceAndEventId(WebhookEventSource source, String eventId);
}
