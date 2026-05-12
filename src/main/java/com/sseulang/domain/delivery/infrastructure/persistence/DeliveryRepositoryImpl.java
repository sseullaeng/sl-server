package com.sseulang.domain.delivery.infrastructure.persistence;

import com.sseulang.domain.delivery.domain.DeliveryRepository;
import com.sseulang.domain.delivery.domain.DeliveryRequest;
import com.sseulang.domain.delivery.domain.DeliveryStatus;
import com.sseulang.domain.delivery.domain.DeliveryStatusCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class DeliveryRepositoryImpl implements DeliveryRepository {

    private final DeliveryJpaRepository jpa;

    public DeliveryRepositoryImpl(DeliveryJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public DeliveryRequest save(DeliveryRequest delivery) {
        return jpa.save(delivery);
    }

    @Override
    public Optional<DeliveryRequest> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<DeliveryRequest> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id);
    }

    @Override
    public int acceptIfStillOpen(Long deliveryId, Long riderId, LocalDateTime acceptedAt) {
        return jpa.acceptIfStillOpen(deliveryId, riderId, acceptedAt);
    }

    @Override
    public int cancelIfStillOpen(Long deliveryId, Long requesterId, LocalDateTime canceledAt, String reason) {
        return jpa.cancelIfStillOpen(deliveryId, requesterId, canceledAt, reason);
    }

    @Override
    public Page<DeliveryRequest> findOpenList(Pageable pageable) {
        return jpa.findByStatusOrderByRequestedAtDesc(DeliveryStatus.모집중, pageable);
    }

    @Override
    public Page<DeliveryRequest> findByParticipant(Long userId, Pageable pageable) {
        return jpa.findByParticipant(userId, pageable);
    }

    @Override
    public List<DeliveryStatusCount> countGroupByStatus() {
        return jpa.countGroupByStatusJpql();
    }

    @Override
    public long sumSettledFee() {
        Long sum = jpa.sumSettledFeeJpql();
        return sum != null ? sum : 0L;
    }

    @Override
    public Optional<DeliveryRequest> findByEscrowApplicationId(Long escrowApplicationId) {
        return jpa.findByEscrowApplicationId(escrowApplicationId);
    }

    @Override
    public java.util.List<DeliveryRequest> findByEscrowApplicationIdIn(java.util.Collection<Long> escrowApplicationIds) {
        if (escrowApplicationIds == null || escrowApplicationIds.isEmpty()) return java.util.List.of();
        return jpa.findByEscrowApplicationIdIn(escrowApplicationIds);
    }

    @Override
    public Page<DeliveryRequest> adminSearch(
            DeliveryStatus status, Long riderId, Long requesterId,
            LocalDateTime createdAfter, LocalDateTime createdBefore,
            String sort, Pageable pageable
    ) {
        org.springframework.data.domain.Sort order = "picked_up_desc".equals(sort)
                ? org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.desc("pickedUpAt").nullsLast(),
                        org.springframework.data.domain.Sort.Order.desc("id"))
                : org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.desc("requestedAt"),
                        org.springframework.data.domain.Sort.Order.desc("id"));
        org.springframework.data.domain.PageRequest pr = org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), order);
        return jpa.adminSearch(status, riderId, requesterId, createdAfter, createdBefore, pr);
    }

    @Override
    public long countCreatedSince(LocalDateTime since) {
        return jpa.countCreatedSince(since);
    }
}
