package com.sseulang.domain.delivery.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DeliveryRepository {

    DeliveryRequest save(DeliveryRequest delivery);

    Optional<DeliveryRequest> findById(Long id);

    

    Optional<DeliveryRequest> findByIdForUpdate(Long id);

    

    int acceptIfStillOpen(Long deliveryId, Long riderId, LocalDateTime acceptedAt);

    

    int cancelIfStillOpen(Long deliveryId, Long requesterId, LocalDateTime canceledAt, String reason);

    
    Page<DeliveryRequest> findOpenList(Pageable pageable);

    
    Page<DeliveryRequest> findByParticipant(Long userId, Pageable pageable);

    
    List<DeliveryStatusCount> countGroupByStatus();

    
    long sumSettledFee();

    
    Optional<DeliveryRequest> findByEscrowApplicationId(Long escrowApplicationId);
}
