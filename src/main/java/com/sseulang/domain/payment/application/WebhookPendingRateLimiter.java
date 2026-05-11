package com.sseulang.domain.payment.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class WebhookPendingRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(WebhookPendingRateLimiter.class);

    
    static final int MAX_PER_WINDOW = 5;

    
    static final long WINDOW_MILLIS = 60_000L;

    
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final ConcurrentMap<String, Slot> slots = new ConcurrentHashMap<>();
    private final Clock clock;

    public WebhookPendingRateLimiter() {
        this(Clock.systemUTC());
    }

    
    public WebhookPendingRateLimiter(Clock clock) {
        this.clock = clock;
    }

    

    public boolean tryAcquire(String orderId) {
        long now = clock.millis();
        if (slots.size() > CLEANUP_THRESHOLD) {
            slots.values().removeIf(s -> s.expiresAt < now);
        }
        Slot slot = slots.compute(orderId, (k, v) -> {
            if (v == null || v.expiresAt < now) {
                Slot fresh = new Slot();
                fresh.count = 1;
                fresh.expiresAt = now + WINDOW_MILLIS;
                return fresh;
            }
            v.count++;
            return v;
        });
        if (slot.count > MAX_PER_WINDOW) {
            log.warn("[toss-webhook] pending orderId={} lookup 한도 초과 (count={})", orderId, slot.count);
            return false;
        }
        return true;
    }

    
    public void release(String orderId) {
        slots.remove(orderId);
    }

    private static class Slot {
        int count;
        long expiresAt;
    }
}
