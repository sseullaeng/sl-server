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

    java.util.List<DeliveryRequest> findByEscrowApplicationIdIn(java.util.Collection<Long> escrowApplicationIds);

    // 라운드 12 — admin delivery 검색.
    Page<DeliveryRequest> adminSearch(
            DeliveryStatus status, Long riderId, Long requesterId,
            LocalDateTime createdAfter, LocalDateTime createdBefore,
            String sort,   // "latest" | "picked_up_desc"
            Pageable pageable
    );

    long countCreatedSince(LocalDateTime since);
}
