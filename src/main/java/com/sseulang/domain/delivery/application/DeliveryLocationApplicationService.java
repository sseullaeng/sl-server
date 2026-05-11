package com.sseulang.domain.delivery.application;

import com.sseulang.domain.delivery.domain.DeliveryLocation;
import com.sseulang.domain.delivery.domain.DeliveryLocationCache;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import com.sseulang.global.websocket.RealtimePublisher;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DeliveryLocationApplicationService {

    
    private static final Duration MIN_PUBLISH_INTERVAL = Duration.ofSeconds(1);
    
    private static final Duration MAX_RECORDED_AT_DRIFT = Duration.ofSeconds(60);

    private final DeliveryApplicationService deliveryApplicationService;
    private final DeliveryLocationCache locationCache;
    private final RealtimePublisher realtimePublisher;
    private final Clock clock;

    
    private final ConcurrentHashMap<String, Long> lastPublishMillis = new ConcurrentHashMap<>();

    public DeliveryLocationApplicationService(
            DeliveryApplicationService deliveryApplicationService,
            DeliveryLocationCache locationCache,
            RealtimePublisher realtimePublisher,
            Clock clock
    ) {
        this.deliveryApplicationService = deliveryApplicationService;
        this.locationCache = locationCache;
        this.realtimePublisher = realtimePublisher;
        this.clock = clock;
    }

    

    public void publishLocation(
            Long deliveryId, Long riderId,
            double latitude, double longitude,
            Double accuracyM, Instant recordedAt
    ) {
        deliveryApplicationService.requireRiderTrackable(deliveryId, riderId);

        Instant now = Instant.now(clock);
        Instant ts = sanitizeRecordedAt(recordedAt, now);

        if (!tryAcquireRate(deliveryId, riderId, now.toEpochMilli())) {
            throw new BusinessException(ErrorCode.DELIVERY_LOCATION_TOO_FREQUENT);
        }

        DeliveryLocation location = new DeliveryLocation(latitude, longitude, accuracyM, ts);
        locationCache.save(deliveryId, location);
        realtimePublisher.publishDeliveryLocation(deliveryId, location);
    }

    
    public Optional<DeliveryLocation> findLast(Long deliveryId, Long viewerId) {
        deliveryApplicationService.requireParticipantTrackable(deliveryId, viewerId);
        return locationCache.findLast(deliveryId);
    }

    

    private static Instant sanitizeRecordedAt(Instant recordedAt, Instant now) {
        if (recordedAt == null) {
            return now;
        }
        Duration diff = Duration.between(recordedAt, now).abs();
        return diff.compareTo(MAX_RECORDED_AT_DRIFT) > 0 ? now : recordedAt;
    }

    

    private boolean tryAcquireRate(Long deliveryId, Long riderId, long nowMillis) {
        String key = deliveryId + ":" + riderId;
        long minInterval = MIN_PUBLISH_INTERVAL.toMillis();
        boolean[] passed = { false };
        lastPublishMillis.compute(key, (k, last) -> {
            if (last == null || nowMillis - last >= minInterval) {
                passed[0] = true;
                return nowMillis;
            }
            return last;  
        });
        return passed[0];
    }
}
