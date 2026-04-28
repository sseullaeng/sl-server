package com.sseulang.domain.auth.infrastructure.persistence;

import com.sseulang.domain.auth.domain.AccessTokenBlacklist;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

/**
 * Redis 백엔드. Key 패턴: {@code auth:atbl:{jti}} → marker "1", TTL = AT 잔여 만료시간.
 * jti 가 글로벌 유니크라 user 차원의 prefix 불필요.
 */
@Repository
public class RedisAccessTokenBlacklist implements AccessTokenBlacklist {

    private static final String KEY_PREFIX = "auth:atbl:";
    private static final String MARKER = "1";

    private final StringRedisTemplate redis;

    public RedisAccessTokenBlacklist(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void blacklist(String jti, Duration ttl) {
        redis.opsForValue().set(KEY_PREFIX + jti, MARKER, ttl);
    }

    @Override
    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + jti));
    }
}
