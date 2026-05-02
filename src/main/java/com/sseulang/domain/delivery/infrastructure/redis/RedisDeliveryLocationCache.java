package com.sseulang.domain.delivery.infrastructure.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sseulang.domain.delivery.domain.DeliveryLocation;
import com.sseulang.domain.delivery.domain.DeliveryLocationCache;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * {@link DeliveryLocationCache} Redis 어댑터. JSON 직렬화 + TTL 30분.
 *
 * <p>키 패턴: {@code delivery:loc:{deliveryId}}. {@code save} 호출마다 TTL 갱신
 * (라이더가 publish 멈추면 30분 후 자동 정리).</p>
 *
 * <p>Redis 미가용 / 직렬화 실패 시 RuntimeException — 호출자(ApplicationService) 가
 * trans 안에서 호출하지만 broadcast 실패는 critical 아니라 호출자 측에서 catch 해 무시 가능.
 * 본 어댑터는 단순 에러 그대로 던짐.</p>
 */
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
            // 손상된 JSON — 삭제 + 빈 응답
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
