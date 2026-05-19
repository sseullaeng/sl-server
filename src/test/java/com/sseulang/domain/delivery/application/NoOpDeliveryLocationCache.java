package com.sseulang.domain.delivery.application;

import com.sseulang.domain.delivery.domain.DeliveryLocation;
import com.sseulang.domain.delivery.domain.DeliveryLocationCache;

import java.util.Optional;

/**
 * 단위/슬라이스 테스트용 fake — Redis 없이 컨텍스트만 채우는 no-op.
 * save/evict 모두 무시. findLast 항상 빈 Optional.
 */
public class NoOpDeliveryLocationCache implements DeliveryLocationCache {
    @Override public void save(Long deliveryId, DeliveryLocation location) { }
    @Override public Optional<DeliveryLocation> findLast(Long deliveryId) { return Optional.empty(); }
    @Override public void evict(Long deliveryId) { }
}
