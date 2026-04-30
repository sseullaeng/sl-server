package com.sseulang.domain.delivery.infrastructure.persistence;

import com.sseulang.domain.delivery.domain.DeliveryRepository;
import com.sseulang.domain.delivery.domain.DeliveryRequest;
import com.sseulang.domain.delivery.domain.DeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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
    public int acceptIfStillOpen(Long deliveryId, Long riderId, LocalDateTime acceptedAt) {
        return jpa.acceptIfStillOpen(deliveryId, riderId, acceptedAt);
    }

    @Override
    public Page<DeliveryRequest> findOpenList(Pageable pageable) {
        return jpa.findByStatusOrderByRequestedAtDesc(DeliveryStatus.모집중, pageable);
    }

    @Override
    public Page<DeliveryRequest> findByParticipant(Long userId, Pageable pageable) {
        return jpa.findByParticipant(userId, pageable);
    }
}
