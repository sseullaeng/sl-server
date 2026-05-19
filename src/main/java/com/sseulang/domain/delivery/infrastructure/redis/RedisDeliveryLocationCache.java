package com.sseulang.domain.delivery.infrastructure.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sseulang.domain.delivery.domain.DeliveryLocation;
import com.sseulang.domain.delivery.domain.DeliveryLocationCache;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
class RedisDeliveryLocationCache implements DeliveryLocationCache {

    private static final String KEY_PREFIX = "delivery:loc:";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    RedisDeliveryLocationCache(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(Long deliveryId, DeliveryLocation location) {
        try {
            String json = objectMapper.writeValueAsString(location);
            redis.opsForValue().set(key(deliveryId), json, TTL);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("DeliveryLocation 직렬화 실패 deliveryId=" + deliveryId, e);
        }
    }

    @Override
    public Optional<DeliveryLocation> findLast(Long deliveryId) {
        String json = redis.opsForValue().get(key(deliveryId));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, DeliveryLocation.class));
        } catch (JsonProcessingException e) {
            
            redis.delete(key(deliveryId));
            return Optional.empty();
        }
    }

    @Override
    public void evict(Long deliveryId) {
        redis.delete(key(deliveryId));
    }

    private static String key(Long deliveryId) {
        return KEY_PREFIX + deliveryId;
    }
}
