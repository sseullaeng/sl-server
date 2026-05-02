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

/**
 * 배달 실시간 위치 publish/subscribe 흐름 (follow-up #51).
 *
 * <ul>
 *   <li>{@link #publishLocation} — 라이더가 STOMP SEND 또는 REST 로 좌표 push.
 *       권한+상태 검증(rider 본인 + canTrackLocation) → recordedAt drift 정정 → rate limit →
 *       좌표 검증({@link DeliveryLocation} VO) → Redis 캐시 → STOMP broadcast.</li>
 *   <li>{@link #findLast} — 참여자가 마지막 위치 조회 (재연결 / 최초 진입). 추적 가능 상태에서만.</li>
 * </ul>
 *
 * <p><b>저장 정책</b>: DB 저장 X — Redis 휘발 캐시 (TTL 30분, 마지막 1건만). history 보관 안 함.
 * 정산완료/취소 시 즉시 evict ({@link DeliveryApplicationService#complete}/{@code cancel}).</p>
 *
 * <p><b>Rate limit</b>: 같은 (deliveryId, riderId) 에 대해 최소 publish 간격 1초. in-memory 라
 * 다중 인스턴스 배포 시 인스턴스 별 한도 — 라이더 권장 5초 주기 가이드와 합쳐 충분 (필요 시 follow-up
 * 으로 Redis SETNX 전환).</p>
 *
 * <p><b>recordedAt drift</b>: 서버 시각 ± 60초만 허용 — 디바이스 시계 어긋남이나 미래/과거 시각
 * 위변조 차단. drift 초과 시 서버 시각으로 덮어쓰기 (DELIVERY_LOCATION_INVALID 던지지 않음 —
 * UX 측 흐름 끊지 않기 위함).</p>
 */
@Service
public class DeliveryLocationApplicationService {

    /** 같은 (deliveryId, riderId) 최소 publish 간격. */
    private static final Duration MIN_PUBLISH_INTERVAL = Duration.ofSeconds(1);
    /** recordedAt 허용 drift (서버 시각 기준 ±). */
    private static final Duration MAX_RECORDED_AT_DRIFT = Duration.ofSeconds(60);

    private final DeliveryApplicationService deliveryApplicationService;
    private final DeliveryLocationCache locationCache;
    private final RealtimePublisher realtimePublisher;
    private final Clock clock;

    /** 라이더 publish 빈도 제한용 in-memory 카운터. key = "{deliveryId}:{riderId}". value = last epoch millis. */
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

    /**
     * 라이더 좌표 push. rider + canTrackLocation 검증 → drift 정정 → rate limit → 좌표 검증 → 캐시 + broadcast.
     * 빈도 제한 초과 시 {@link ErrorCode#DELIVERY_LOCATION_TOO_FREQUENT}.
     */
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

    /** 참여자 + 추적 가능 상태일 때만 마지막 위치 조회. 캐시 미존재 시 빈 Optional. */
    public Optional<DeliveryLocation> findLast(Long deliveryId, Long viewerId) {
        deliveryApplicationService.requireParticipantTrackable(deliveryId, viewerId);
        return locationCache.findLast(deliveryId);
    }

    /**
     * recordedAt 이 서버 시각 기준 ±{@value #MAX_RECORDED_AT_DRIFT} 초 안이면 그대로,
     * drift 초과 / null 이면 서버 시각으로 정정. UX 흐름 안 끊고 디바이스 시계 어긋남 방어.
     */
    private static Instant sanitizeRecordedAt(Instant recordedAt, Instant now) {
        if (recordedAt == null) {
            return now;
        }
        Duration diff = Duration.between(recordedAt, now).abs();
        return diff.compareTo(MAX_RECORDED_AT_DRIFT) > 0 ? now : recordedAt;
    }

    /**
     * 같은 (deliveryId, riderId) 의 직전 publish 시각과 비교. 1초 이내 재호출은 거부.
     * ConcurrentHashMap.compute 로 atomic — 동시 호출 1개만 통과. 결과는 array 캡처로 외부 전달.
     */
    private boolean tryAcquireRate(Long deliveryId, Long riderId, long nowMillis) {
        String key = deliveryId + ":" + riderId;
        long minInterval = MIN_PUBLISH_INTERVAL.toMillis();
        boolean[] passed = { false };
        lastPublishMillis.compute(key, (k, last) -> {
            if (last == null || nowMillis - last >= minInterval) {
                passed[0] = true;
                return nowMillis;
            }
            return last;  // 변경 안 함 — 거부 (passed 그대로 false)
        });
        return passed[0];
    }
}
