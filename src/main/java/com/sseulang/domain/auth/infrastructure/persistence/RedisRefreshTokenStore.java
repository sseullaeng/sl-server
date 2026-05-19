package com.sseulang.domain.auth.infrastructure.persistence;

import com.sseulang.domain.auth.domain.RefreshTokenStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

@Repository
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String KEY_PREFIX = "auth:rt:";
    private static final String VERSION_KEY_PREFIX = "auth:rtv:";
    private static final String MARKER = "1";
    

    private static final Duration VERSION_KEY_TTL_AFTER_REVOKE = Duration.ofDays(30);

    

    private static final RedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>(
            """
            local cur = redis.call('GET', KEYS[1])
            if cur == false then cur = '0' end
            if cur ~= ARGV[1] then return -1 end
            return redis.call('DEL', KEYS[2])
            """,
            Long.class
    );

    private final StringRedisTemplate redis;

    public RedisRefreshTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long currentTokenVersion(String role, Long userId) {
        String v = redis.opsForValue().get(versionKey(role, userId));
        if (v == null) {
            return 0L;
        }
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            
            return 0L;
        }
    }

    @Override
    public void save(String role, Long userId, String jti, Duration ttl) {
        redis.opsForValue().set(jtiKey(role, userId, jti), MARKER, ttl);
        
        
        String vKey = versionKey(role, userId);
        if (Boolean.TRUE.equals(redis.hasKey(vKey))) {
            redis.expire(vKey, ttl);
        } else {
            redis.opsForValue().setIfAbsent(vKey, "0", ttl);
        }
    }

    @Override
    public boolean consume(String role, Long userId, String jti, long claimTv) {
        Long result = redis.execute(
                CONSUME_SCRIPT,
                List.of(versionKey(role, userId), jtiKey(role, userId, jti)),
                Long.toString(claimTv)
        );
        
        return result != null && result == 1L;
    }

    @Override
    public void revoke(String role, Long userId, String jti) {
        redis.delete(jtiKey(role, userId, jti));
    }

    @Override
    public void revokeAll(String role, Long userId) {
        
        
        String vKey = versionKey(role, userId);
        redis.opsForValue().increment(vKey);
        
        redis.expire(vKey, VERSION_KEY_TTL_AFTER_REVOKE);
    }

    private static String jtiKey(String role, Long userId, String jti) {
        return KEY_PREFIX + normalize(role) + ":" + userId + ":" + jti;
    }

    private static String versionKey(String role, Long userId) {
        return VERSION_KEY_PREFIX + normalize(role) + ":" + userId;
    }

    private static String normalize(String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role 은 필수입니다");
        }
        return role.toUpperCase(Locale.ROOT);
    }
}
