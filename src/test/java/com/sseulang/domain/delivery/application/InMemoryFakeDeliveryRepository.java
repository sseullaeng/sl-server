package com.sseulang.domain.delivery.application;

import com.sseulang.domain.delivery.domain.DeliveryRepository;
import com.sseulang.domain.delivery.domain.DeliveryRequest;
import com.sseulang.domain.delivery.domain.DeliveryStatus;
import com.sseulang.domain.delivery.domain.DeliveryStatusCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryFakeDeliveryRepository implements DeliveryRepository {

    private final Map<Long, DeliveryRequest> store = new HashMap<>();
    private long sequence = 0;

    @Override
    public DeliveryRequest save(DeliveryRequest delivery) {
        if (delivery.getId() == null) {
            ReflectionTestUtils.setField(delivery, "id", ++sequence);
        }
        store.put(delivery.getId(), delivery);
        return delivery;
    }

    @Override
    public Optional<DeliveryRequest> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<DeliveryRequest> findByIdForUpdate(Long id) {
        // fake — 락 의미 X. prod 동시성 IT 에서 실제 PESSIMISTIC_WRITE 검증.
        return findById(id);
    }

    @Override
    public synchronized int acceptIfStillOpen(Long deliveryId, Long riderId, LocalDateTime acceptedAt) {
        DeliveryRequest d = store.get(deliveryId);
        if (d == null) return 0;
        if (d.getStatus() != DeliveryStatus.모집중) return 0;
        if (riderId == null || riderId.equals(d.getRequesterId())) return 0;
        d.acceptBy(riderId, acceptedAt);
        return 1;
    }

    @Override
    public synchronized int cancelIfStillOpen(Long deliveryId, Long requesterId, LocalDateTime canceledAt, String reason) {
        DeliveryRequest d = store.get(deliveryId);
        if (d == null) return 0;
        if (!requesterId.equals(d.getRequesterId())) return 0;
        if (d.getStatus() != DeliveryStatus.모집중) return 0;
        d.cancelByRequester(canceledAt, reason);
        return 1;
    }

    @Override
    public Page<DeliveryRequest> findOpenList(Pageable pageable) {
        List<DeliveryRequest> filtered = store.values().stream()
                .filter(d -> d.getStatus() == DeliveryStatus.모집중)
                .sorted(Comparator.comparing(DeliveryRequest::getRequestedAt).reversed())
                .toList();
        return new PageImpl<>(filtered, pageable, filtered.size());
    }

    @Override
    public Page<DeliveryRequest> findByParticipant(Long userId, Pageable pageable) {
        List<DeliveryRequest> filtered = store.values().stream()
                .filter(d -> userId.equals(d.getRequesterId())
                        || (d.getRiderId() != null && userId.equals(d.getRiderId())))
                .sorted(Comparator.comparing(DeliveryRequest::getRequestedAt).reversed())
                .toList();
        return new PageImpl<>(filtered, pageable, filtered.size());
    }

    @Override
    public List<DeliveryStatusCount> countGroupByStatus() {
        Map<DeliveryStatus, Long> grouped = new EnumMap<>(DeliveryStatus.class);
        for (DeliveryRequest d : store.values()) {
            grouped.merge(d.getStatus(), 1L, Long::sum);
        }
        return grouped.entrySet().stream()
                .map(e -> new DeliveryStatusCount(e.getKey(), e.getValue()))
                .toList();
    }

    @Override
    public long sumSettledFee() {
        return store.values().stream()
                .filter(d -> d.getStatus() == DeliveryStatus.정산완료)
                .mapToLong(DeliveryRequest::getFee)
                .sum();
    }

    @Override
    public java.util.Optional<DeliveryRequest> findByEscrowApplicationId(Long escrowApplicationId) {
        return store.values().stream()
                .filter(d -> escrowApplicationId.equals(d.getEscrowApplicationId()))
                .findFirst();
    }

    @Override
    public java.util.List<DeliveryRequest> findByEscrowApplicationIdIn(java.util.Collection<Long> escrowApplicationIds) {
        if (escrowApplicationIds == null || escrowApplicationIds.isEmpty()) return java.util.List.of();
        return store.values().stream()
                .filter(d -> d.getEscrowApplicationId() != null && escrowApplicationIds.contains(d.getEscrowApplicationId()))
                .toList();
    }
}
