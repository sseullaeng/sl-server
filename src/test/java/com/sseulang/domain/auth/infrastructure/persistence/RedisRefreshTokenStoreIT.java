package com.sseulang.domain.auth.infrastructure.persistence;

import com.sseulang.support.RedisIntegrationTestBase;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class RedisRefreshTokenStoreIT extends RedisIntegrationTestBase {

    private static final Long USER_ID = 42L;
    private static final Duration TTL = Duration.ofMinutes(30);

    private RedisRefreshTokenStore store;

    @BeforeEach
    void setUp() {
        store = new RedisRefreshTokenStore(redisTemplate());
    }

    @Test
    @DisplayName("save 후 consume_true 반환 (atomic 폐기)")
    void consume_save된jti_true() {
        String jti = UUID.randomUUID().toString();
        store.save(USER_ID, jti, TTL);

        boolean consumed = store.consume(USER_ID, jti);

        assertThat(consumed).isTrue();
        // 같은 jti 다시 consume → false (one-time)
        assertThat(store.consume(USER_ID, jti)).isFalse();
    }

    @Test
    @DisplayName("save 안 한 jti consume_false")
    void consume_save안한jti_false() {
        assertThat(store.consume(USER_ID, UUID.randomUUID().toString())).isFalse();
    }

    @Test
    @DisplayName("revokeAll_해당 user 의 모든 jti 사라짐")
    void revokeAll_user전체폐기() {
        List<String> jtis = IntStream.range(0, 5)
                .mapToObj(i -> UUID.randomUUID().toString())
                .toList();
        jtis.forEach(jti -> store.save(USER_ID, jti, TTL));
        // 다른 user 의 jti — 영향받지 않아야 함
        Long otherUser = 99L;
        String otherJti = UUID.randomUUID().toString();
        store.save(otherUser, otherJti, TTL);

        store.revokeAll(USER_ID);

        jtis.forEach(jti -> assertThat(store.consume(USER_ID, jti)).isFalse());
        // 다른 user 의 jti 는 살아있어야 함
        assertThat(store.consume(otherUser, otherJti)).isTrue();
    }

    @Test
    @DisplayName("revoke_단일 jti 폐기")
    void revoke_단일폐기() {
        String jti = UUID.randomUUID().toString();
        store.save(USER_ID, jti, TTL);

        store.revoke(USER_ID, jti);

        assertThat(store.consume(USER_ID, jti)).isFalse();
    }

    @Test
    @DisplayName("TTL 만료_자동 정리")
    void save_TTL_만료시_사라짐() {
        String jti = UUID.randomUUID().toString();
        store.save(USER_ID, jti, Duration.ofSeconds(1));

        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> !store.consume(USER_ID, jti));
    }

    @Test
    @DisplayName("동시 consume race_정확히 1개 스레드만 true")
    void consume_race_one_winner() throws Exception {
        String jti = UUID.randomUUID().toString();
        store.save(USER_ID, jti, TTL);

        int threadCount = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger trueCount = new AtomicInteger(0);

        try {
            List<CompletableFuture<Boolean>> futures = IntStream.range(0, threadCount)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                        ready.countDown();
                        try {
                            start.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return false;
                        }
                        boolean ok = store.consume(USER_ID, jti);
                        if (ok) trueCount.incrementAndGet();
                        return ok;
                    }, pool))
                    .toList();

            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdown();
        }

        assertThat(trueCount.get())
                .as("Redis DEL 의 atomic 보장으로 정확히 1개 스레드만 consume=true 를 받아야 한다")
                .isEqualTo(1);
    }
}
