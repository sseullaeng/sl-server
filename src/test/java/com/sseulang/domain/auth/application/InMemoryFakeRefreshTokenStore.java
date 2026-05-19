package com.sseulang.domain.auth.application;

import com.sseulang.domain.auth.domain.RefreshTokenStore;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 단위 테스트용 in-memory fake. TTL 무시 (테스트 시간 짧음).
 *
 * <p>(role, userId, jti) 격리 + tokenVersion 의미 보장 — Redis 구현과 동일한 race-free 의미.
 * revokeAll = INCR. consume 은 (claimTv == currentTv) 검증 후 jti 폐기.</p>
 */
class InMemoryFakeRefreshTokenStore implements RefreshTokenStore {

    private final Set<String> jtiStore = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> versionStore = new ConcurrentHashMap<>();

    @Override
    public long currentTokenVersion(String role, Long userId) {
        return versionStore.getOrDefault(versionKey(role, userId), 0L);
    }

    @Override
    public void save(String role, Long userId, String jti, Duration ttl) {
        jtiStore.add(jtiKey(role, userId, jti));
    }

    @Override
    public boolean consume(String role, Long userId, String jti, long claimTv) {
        // tv mismatch 면 jti 가 살아있어도 consume 실패 — Redis Lua 와 동일 의미.
        if (currentTokenVersion(role, userId) != claimTv) {
            return false;
        }
        return jtiStore.remove(jtiKey(role, userId, jti));
    }

    @Override
    public void revoke(String role, Long userId, String jti) {
        jtiStore.remove(jtiKey(role, userId, jti));
    }

    @Override
    public void revokeAll(String role, Long userId) {
        versionStore.merge(versionKey(role, userId), 1L, Long::sum);
    }

    boolean contains(String role, Long userId, String jti) {
        return jtiStore.contains(jtiKey(role, userId, jti));
    }

    int size() {
        return jtiStore.size();
    }

    private static String jtiKey(String role, Long userId, String jti) {
        return normalize(role) + "::" + userId + "::" + jti;
    }

    private static String versionKey(String role, Long userId) {
        return normalize(role) + "::" + userId;
    }

    private static String normalize(String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role 은 필수입니다");
        }
        return role.toUpperCase(Locale.ROOT);
    }
}
