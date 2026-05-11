package com.sseulang.domain.delivery.domain;

import java.util.Optional;

public interface DeliveryLocationCache {

    
    void save(Long deliveryId, DeliveryLocation location);

    
    Optional<DeliveryLocation> findLast(Long deliveryId);

    
    void evict(Long deliveryId);
}
