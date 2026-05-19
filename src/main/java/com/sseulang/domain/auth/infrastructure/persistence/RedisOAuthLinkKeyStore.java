package com.sseulang.domain.auth.infrastructure.persistence;

import com.sseulang.domain.auth.domain.OAuthLinkKeyStore;
import com.sseulang.domain.user.domain.SocialProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
public class RedisOAuthLinkKeyStore implements OAuthLinkKeyStore {

    private static final String KEY_PREFIX = "auth:oauth-link:";
    private static final String SEP = "|";

    private final StringRedisTemplate redis;

    public RedisOAuthLinkKeyStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(String key, Long userId, SocialProvider provider, String providerId, Duration ttl) {
        if (key == null || key.isBlank() || userId == null || provider == null
                || providerId == null || providerId.isBlank() || ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("link key 저장 인자 부정");
        }
        String value = userId + SEP + provider.name() + SEP + providerId;
        redis.opsForValue().set(redisKey(key), value, ttl);
    }

    @Override
    public Optional<LinkKeyValue> consume(String key) {
        if (key == null || key.isBlank()) return Optional.empty();
        String value = redis.opsForValue().getAndDelete(redisKey(key));
        if (value == null) return Optional.empty();
        String[] parts = value.split("\\" + SEP, 3);
        if (parts.length != 3) return Optional.empty();
        try {
            return Optional.of(new LinkKeyValue(
                    Long.parseLong(parts[0]),
                    SocialProvider.valueOf(parts[1]),
                    parts[2]
            ));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String redisKey(String key) {
        return KEY_PREFIX + key;
    }
}
