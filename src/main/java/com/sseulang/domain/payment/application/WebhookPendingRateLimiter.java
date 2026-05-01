package com.sseulang.domain.payment.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * pending Payment.orderId 당 webhook lookup outbound 시도 제한.
 *
 * <p>게이트 1 round 4 — DoS: 공격자가 본인 pending 만든 후 결제하지 않고 random paymentKey +
 * unique transmission-id 반복 보내면 매번 webhook_events INSERT + Toss lookup outbound 발생.
 * 사전 status=대기 가드는 정상 결제 1회 후엔 막지만, pending 유지 동안은 무방비.</p>
 *
 * <p>한도: orderId 당 60초 윈도우에 {@value #MAX_PER_WINDOW} 회. 초과 시 false 반환 → 호출자가
 * 사전 거부 (저장 / lookup 모두 skip). 정상 흐름은 토스가 같은 transmission-id 로 retry 하므로
 * UNIQUE 멱등 가드에 잡혀 lookup 도 1회만 — 한도에 걸릴 일 없음.</p>
 *
 * <p><b>단일 JVM 한정.</b> 다중 인스턴스 prod 에서는 Redis 카운터로 교체 (follow-up #53).</p>
 */
@Component
public class WebhookPendingRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(WebhookPendingRateLimiter.class);

    /** orderId 당 윈도우 안 lookup 시도 한도. */
    static final int MAX_PER_WINDOW = 5;

    /** 윈도우 길이 (밀리초). */
    static final long WINDOW_MILLIS = 60_000L;

    /** 자체 청소 임계 — 누적 키 수가 초과하면 만료된 항목 정리 (메모리 누수 차단). */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final ConcurrentMap<String, Slot> slots = new ConcurrentHashMap<>();
    private final Clock clock;

    public WebhookPendingRateLimiter() {
        this(Clock.systemUTC());
    }

    /** 테스트용 — Clock 주입. Spring 은 본 생성자 호출 X. */
    public WebhookPendingRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * 시도 허용 여부. true = 허용 (호출자가 진행), false = 한도 초과 (호출자가 skip).
     */
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

    /** 결제 완료 후 즉시 카운터 비움 — 메모리 빠른 회수. */
    public void release(String orderId) {
        slots.remove(orderId);
    }

    private static class Slot {
        int count;
        long expiresAt;
    }
}
