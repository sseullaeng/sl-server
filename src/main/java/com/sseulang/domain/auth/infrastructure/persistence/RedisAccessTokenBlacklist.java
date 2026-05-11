package com.sseulang.domain.auth.infrastructure.persistence;

import com.sseulang.domain.auth.domain.AccessTokenBlacklist;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

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
