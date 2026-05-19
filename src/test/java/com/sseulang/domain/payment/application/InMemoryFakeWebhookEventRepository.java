package com.sseulang.domain.payment.application;

import com.sseulang.domain.payment.domain.WebhookEvent;
import com.sseulang.domain.payment.domain.WebhookEventRepository;
import com.sseulang.domain.payment.domain.WebhookEventSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryFakeWebhookEventRepository implements WebhookEventRepository {

    private final Map<String, WebhookEvent> store = new HashMap<>();
    private long sequence = 0;

    @Override
    public WebhookEvent save(WebhookEvent event) {
        String key = event.getSource() + ":" + event.getEventId();
        if (event.getId() == null) {
            if (store.containsKey(key)) {
                // UNIQUE 충돌 시뮬 — JPA 가 던지는 예외 동일.
                throw new DataIntegrityViolationException("duplicate eventId: " + key);
            }
            ReflectionTestUtils.setField(event, "id", ++sequence);
        }
        store.put(key, event);
        return event;
    }

    @Override
    public Optional<WebhookEvent> findBySourceAndEventId(WebhookEventSource source, String eventId) {
        return Optional.ofNullable(store.get(source + ":" + eventId));
    }
}
