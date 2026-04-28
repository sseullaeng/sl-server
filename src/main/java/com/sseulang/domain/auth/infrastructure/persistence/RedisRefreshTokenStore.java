package com.sseulang.domain.auth.infrastructure.persistence;

import com.sseulang.domain.auth.domain.RefreshTokenStore;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * Redis 백엔드. Key 패턴: {@code auth:rt:{userId}:{jti}} → marker "1", TTL = 토큰 유효기간.
 * revokeAll 은 SCAN 으로 점진 검색 (KEYS 명령 회피).
 */
@Repository
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String KEY_PREFIX = "auth:rt:";
    private static final String MARKER = "1";
    private static final long SCAN_BATCH = 100L;

    private final StringRedisTemplate redis;

    public RedisRefreshTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(Long userId, String jti, Duration ttl) {
        redis.opsForValue().set(key(userId, jti), MARKER, ttl);
    }

    @Override
    public boolean consume(Long userId, String jti) {
        // DEL 의 반환값 = 삭제된 키 개수. 1 이면 이번 호출이 jti 를 가진 첫 호출자.
        // 같은 jti 에 대한 동시 호출 중 단 하나만 true 를 받는다 (Redis 가 단일 스레드).
        Long deleted = redis.delete(java.util.List.of(key(userId, jti)));
        return deleted != null && deleted > 0L;
    }

    @Override
    public void revoke(Long userId, String jti) {
        redis.delete(key(userId, jti));
    }

    @Override
    public void revokeAll(Long userId) {
        // TODO(5/6 이후): 토큰 수 증가 시 SCAN 비효율. user 별 SET 인덱스(`auth:rt-idx:{userId}`)로 변경.
        ScanOptions opts = ScanOptions.scanOptions()
                .match(KEY_PREFIX + userId + ":*")
                .count(SCAN_BATCH)
                .build();
        Set<String> keys = new HashSet<>();
        try (Cursor<String> cursor = redis.scan(opts)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private static String key(Long userId, String jti) {
        return KEY_PREFIX + userId + ":" + jti;
    }
}
