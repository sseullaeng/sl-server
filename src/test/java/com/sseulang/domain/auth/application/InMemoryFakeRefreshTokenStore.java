package com.sseulang.domain.auth.application;

import com.sseulang.domain.auth.domain.RefreshTokenStore;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 단위 테스트용 in-memory fake. TTL 무시 (테스트 시간 짧음).
 */
class InMemoryFakeRefreshTokenStore implements RefreshTokenStore {

    private final Set<String> store = ConcurrentHashMap.newKeySet();

    @Override
    public void save(Long userId, String jti, Duration ttl) {
        store.add(key(userId, jti));
    }

    @Override
    public boolean consume(Long userId, String jti) {
        return store.remove(key(userId, jti));  // ConcurrentHashMap.newKeySet() → atomic remove
    }

    @Override
    public void revoke(Long userId, String jti) {
        store.remove(key(userId, jti));
    }

    boolean contains(Long userId, String jti) {
        return store.contains(key(userId, jti));
    }

    @Override
    public void revokeAll(Long userId) {
        String prefix = userId + "::";
        store.removeIf(k -> k.startsWith(prefix));
    }

    int size() {
        return store.size();
    }

    private static String key(Long userId, String jti) {
        return userId + "::" + jti;
    }
}
