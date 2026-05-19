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

    /** 헬퍼 — 현재 tv 로 발급된 RT consume 시뮬레이션. */
    private boolean consumeAtCurrentVersion(String role, Long userId, String jti) {
        return store.consume(role, userId, jti, store.currentTokenVersion(role, userId));
    }

    @Test
    @DisplayName("save 후 consume_true 반환 (atomic 폐기)")
    void consume_save된jti_true() {
        String jti = UUID.randomUUID().toString();
        store.save("USER", USER_ID, jti, TTL);

        boolean consumed = consumeAtCurrentVersion("USER", USER_ID, jti);

        assertThat(consumed).isTrue();
        // 같은 jti 다시 consume → false (one-time)
        assertThat(consumeAtCurrentVersion("USER", USER_ID, jti)).isFalse();
    }

    @Test
    @DisplayName("save 안 한 jti consume_false")
    void consume_save안한jti_false() {
        assertThat(consumeAtCurrentVersion("USER", USER_ID, UUID.randomUUID().toString())).isFalse();
    }

    @Test
    @DisplayName("currentTokenVersion_초기 0 → revokeAll 후 INCR (단조 증가)")
    void tokenVersion_INCR() {
        assertThat(store.currentTokenVersion("USER", USER_ID)).isZero();

        store.revokeAll("USER", USER_ID);
        assertThat(store.currentTokenVersion("USER", USER_ID)).isEqualTo(1L);

        store.revokeAll("USER", USER_ID);
        assertThat(store.currentTokenVersion("USER", USER_ID)).isEqualTo(2L);
    }

    @Test
    @DisplayName("consume_claimTv 가 currentTv 와 mismatch → false (revokeAll 이후 구버전 RT 거부)")
    void consume_tv_mismatch_false() {
        String jti = UUID.randomUUID().toString();
        long oldTv = store.currentTokenVersion("USER", USER_ID);  // 0
        store.save("USER", USER_ID, jti, TTL);

        store.revokeAll("USER", USER_ID);  // tv: 0 → 1

        // 구버전 tv(=0)로 발급된 RT 는 거부되어야 함
        assertThat(store.consume("USER", USER_ID, jti, oldTv)).isFalse();
        // 그리고 jti 키는 살아있다 (DEL 자체가 안 일어남) — TTL 만료까지 잔존하지만 의미상 무효
        // 다음 호출에서도 mismatch 라 여전히 false
        assertThat(store.consume("USER", USER_ID, jti, oldTv)).isFalse();
    }

    @Test
    @DisplayName("revokeAll_해당 (role,user) 의 모든 RT 무효화 (다른 사용자/role 영향 X)")
    void revokeAll_user전체폐기() {
        List<String> jtis = IntStream.range(0, 5)
                .mapToObj(i -> UUID.randomUUID().toString())
                .toList();
        long tv0 = store.currentTokenVersion("USER", USER_ID);
        jtis.forEach(jti -> store.save("USER", USER_ID, jti, TTL));
        // 다른 user — 영향받지 않아야 함
        Long otherUser = 99L;
        String otherJti = UUID.randomUUID().toString();
        long otherTv = store.currentTokenVersion("USER", otherUser);
        store.save("USER", otherUser, otherJti, TTL);

        store.revokeAll("USER", USER_ID);

        // 해당 user 의 기존 RT 는 모두 mismatch → false
        jtis.forEach(jti -> assertThat(store.consume("USER", USER_ID, jti, tv0)).isFalse());
        // 다른 user 의 RT 는 자기 tv 로 정상 consume
        assertThat(store.consume("USER", otherUser, otherJti, otherTv)).isTrue();
    }

    @Test
    @DisplayName("user/admin 동일 id 격리 — role 차원으로 RT 키 분리")
    void user_admin_동일id_격리() {
        Long sharedId = 7L;
        String userJti = UUID.randomUUID().toString();
        String adminJti = UUID.randomUUID().toString();
        long userTv = store.currentTokenVersion("USER", sharedId);
        long adminTv = store.currentTokenVersion("ADMIN", sharedId);
        store.save("USER", sharedId, userJti, TTL);
        store.save("ADMIN", sharedId, adminJti, TTL);

        // user 측 revokeAll → admin 측은 살아있어야 함
        store.revokeAll("USER", sharedId);

        assertThat(store.consume("USER", sharedId, userJti, userTv)).as("user RT 무효화").isFalse();
        assertThat(store.consume("ADMIN", sharedId, adminJti, adminTv)).as("admin RT 살아있음").isTrue();
    }

    @Test
    @DisplayName("role 정규화 — 소문자/대문자 입력이 동일 키로 매핑")
    void role_정규화_대소문자무관() {
        String jti = UUID.randomUUID().toString();
        long tv = store.currentTokenVersion("user", USER_ID);
        store.save("user", USER_ID, jti, TTL);

        // 대문자로 조회해도 같은 슬롯
        assertThat(store.currentTokenVersion("USER", USER_ID)).isEqualTo(tv);
        assertThat(store.consume("USER", USER_ID, jti, tv)).isTrue();
    }

    @Test
    @DisplayName("키 충돌 회귀 — userId=7 의 revokeAll 이 userId=70 의 RT 를 건드리지 않음")
    void revokeAll_id접두사_충돌없음() {
        String jti7 = UUID.randomUUID().toString();
        String jti70 = UUID.randomUUID().toString();
        long tv7 = store.currentTokenVersion("USER", 7L);
        long tv70 = store.currentTokenVersion("USER", 70L);
        store.save("USER", 7L, jti7, TTL);
        store.save("USER", 70L, jti70, TTL);

        store.revokeAll("USER", 7L);

        assertThat(store.consume("USER", 7L, jti7, tv7)).as("userId=7 무효화").isFalse();
        assertThat(store.consume("USER", 70L, jti70, tv70)).as("userId=70 영향 없음").isTrue();
    }

    @Test
    @DisplayName("revoke_단일 jti 폐기")
    void revoke_단일폐기() {
        String jti = UUID.randomUUID().toString();
        long tv = store.currentTokenVersion("USER", USER_ID);
        store.save("USER", USER_ID, jti, TTL);

        store.revoke("USER", USER_ID, jti);

        assertThat(store.consume("USER", USER_ID, jti, tv)).isFalse();
    }

    @Test
    @DisplayName("TTL 만료_자동 정리")
    void save_TTL_만료시_사라짐() {
        String jti = UUID.randomUUID().toString();
        long tv = store.currentTokenVersion("USER", USER_ID);
        store.save("USER", USER_ID, jti, Duration.ofSeconds(1));

        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> !store.consume("USER", USER_ID, jti, tv));
    }

    @Test
    @DisplayName("동시 consume race_정확히 1개 스레드만 true (Lua 원자성)")
    void consume_race_one_winner() throws Exception {
        String jti = UUID.randomUUID().toString();
        long tv = store.currentTokenVersion("USER", USER_ID);
        store.save("USER", USER_ID, jti, TTL);

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
                        boolean ok = store.consume("USER", USER_ID, jti, tv);
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
                .as("Lua DEL 의 atomic 보장으로 정확히 1개 스레드만 consume=true 를 받아야 한다")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("revokeAll race 회귀 — revokeAll 진행 중 동시 save 된 RT 도 무효화 (이전 SCAN 구현 race 제거)")
    void revokeAll_race_새로_save된_RT도_무효화() throws Exception {
        // 시나리오: revokeAll 직전에 Tv=0 으로 발급된 RT 한 다발 + revokeAll 실행 중 새 save 시도.
        // 새 RT 도 발급 시점 tv=0 이라면 revokeAll 의 INCR 직후 모두 mismatch 로 거부되어야 함.
        long tv0 = store.currentTokenVersion("USER", USER_ID);  // 0
        int preCount = 10;
        List<String> preJtis = IntStream.range(0, preCount)
                .mapToObj(i -> UUID.randomUUID().toString())
                .toList();
        preJtis.forEach(j -> store.save("USER", USER_ID, j, TTL));

        int concurrentCount = 10;
        ExecutorService pool = Executors.newFixedThreadPool(concurrentCount + 1);
        CountDownLatch ready = new CountDownLatch(concurrentCount + 1);
        CountDownLatch start = new CountDownLatch(1);

        try {
            // 동시 save 들 — 모두 tv=0 가정으로 발급된 RT.
            List<String> raceJtis = IntStream.range(0, concurrentCount)
                    .mapToObj(i -> UUID.randomUUID().toString())
                    .toList();
            List<CompletableFuture<Void>> saves = raceJtis.stream()
                    .map(j -> CompletableFuture.runAsync(() -> {
                        ready.countDown();
                        try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                        store.save("USER", USER_ID, j, TTL);
                    }, pool))
                    .toList();

            CompletableFuture<Void> revoke = CompletableFuture.runAsync(() -> {
                ready.countDown();
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                store.revokeAll("USER", USER_ID);
            }, pool);

            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            CompletableFuture.allOf(
                    CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new)),
                    revoke
            ).get(5, TimeUnit.SECONDS);

            // 검증: tv 가 INCR 되었으므로 tv0 으로 발급된 모든 RT 는 consume 거부.
            assertThat(store.currentTokenVersion("USER", USER_ID))
                    .as("revokeAll 한 번이라도 실행되었으니 tv >= 1")
                    .isGreaterThanOrEqualTo(1L);

            preJtis.forEach(j -> assertThat(store.consume("USER", USER_ID, j, tv0))
                    .as("revokeAll 이전 발급 RT 거부")
                    .isFalse());
            raceJtis.forEach(j -> assertThat(store.consume("USER", USER_ID, j, tv0))
                    .as("revokeAll 와 race 한 RT 도 tv=0 이면 거부 (이전 SCAN 구현은 살아남았음)")
                    .isFalse());
        } finally {
            pool.shutdown();
        }
    }
}
