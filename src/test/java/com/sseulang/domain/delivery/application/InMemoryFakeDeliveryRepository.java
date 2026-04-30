package com.sseulang.domain.delivery.application;

import com.sseulang.domain.delivery.domain.DeliveryRepository;
import com.sseulang.domain.delivery.domain.DeliveryRequest;
import com.sseulang.domain.delivery.domain.DeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
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
    public synchronized int acceptIfStillOpen(Long deliveryId, Long riderId, LocalDateTime acceptedAt) {
        DeliveryRequest d = store.get(deliveryId);
        if (d == null) return 0;
        if (d.getStatus() != DeliveryStatus.모집중) return 0;
        d.acceptBy(riderId, acceptedAt);
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
}
