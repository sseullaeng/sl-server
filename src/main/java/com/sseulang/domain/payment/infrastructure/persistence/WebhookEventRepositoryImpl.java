package com.sseulang.domain.payment.infrastructure.persistence;

import com.sseulang.domain.payment.domain.WebhookEvent;
import com.sseulang.domain.payment.domain.WebhookEventRepository;
import com.sseulang.domain.payment.domain.WebhookEventSource;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class WebhookEventRepositoryImpl implements WebhookEventRepository {

    private final WebhookEventJpaRepository jpa;

    public WebhookEventRepositoryImpl(WebhookEventJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public WebhookEvent save(WebhookEvent event) {
        
        
        return jpa.saveAndFlush(event);
    }

    @Override
    public Optional<WebhookEvent> findBySourceAndEventId(WebhookEventSource source, String eventId) {
        return jpa.findBySourceAndEventId(source, eventId);
    }
}
