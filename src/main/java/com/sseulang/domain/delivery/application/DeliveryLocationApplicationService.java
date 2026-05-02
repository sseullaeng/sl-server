package com.sseulang.domain.delivery.application;

import com.sseulang.domain.delivery.domain.DeliveryLocation;
import com.sseulang.domain.delivery.domain.DeliveryLocationCache;
import com.sseulang.global.websocket.RealtimePublisher;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * 배달 실시간 위치 publish/subscribe 흐름 (follow-up #51).
 *
 * <ul>
 *   <li>{@link #publishLocation} — 라이더가 STOMP SEND 또는 REST 로 좌표 push.
 *       권한 검증(rider 본인) → 좌표 검증({@link DeliveryLocation} VO) → Redis 캐시 → STOMP broadcast.</li>
 *   <li>{@link #findLast} — 참여자가 마지막 위치 조회 (재연결 / 최초 진입). Redis 캐시만 read.</li>
 * </ul>
 *
 * <p><b>저장 정책</b>: DB 저장 X — Redis 휘발 캐시 (TTL 30분, 마지막 1건만). history 보관 안 함.
 * 정산완료 / 취소 후엔 자동 만료 또는 호출자가 evict.</p>
 *
 * <p><b>동시성</b>: 같은 deliveryId 에 라이더가 여러 device 로 push 하면 마지막 SET 이 win
 * (race 무방 — UX 상 큰 문제 없음). DB 트랜잭션 외부라 잠금 없음.</p>
 */
@Service
public class DeliveryLocationApplicationService {

    private final DeliveryApplicationService deliveryApplicationService;
    private final DeliveryLocationCache locationCache;
    private final RealtimePublisher realtimePublisher;
    private final Clock clock;

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
     * 라이더 좌표 push. 라이더 본인 검증 → 좌표 범위 검증(VO 생성자) → 캐시 + broadcast.
     *
     * <p>{@code recordedAt} 미주입 시 서버 시각으로 채움. 클라이언트 디바이스 시계가 어긋날 수 있어
     * UI 표시는 서버 시각 기준 권장.</p>
     */
    public void publishLocation(
            Long deliveryId, Long riderId,
            double latitude, double longitude,
            Double accuracyM, Instant recordedAt
    ) {
        deliveryApplicationService.requireRider(deliveryId, riderId);
        Instant ts = (recordedAt != null) ? recordedAt : Instant.now(clock);
        DeliveryLocation location = new DeliveryLocation(latitude, longitude, accuracyM, ts);

        locationCache.save(deliveryId, location);
        realtimePublisher.publishDeliveryLocation(deliveryId, location);
    }

    /**
     * 참여자(요청자/라이더) 가 마지막 위치 조회. 캐시 미존재 / TTL 만료 시 빈 Optional.
     */
    public Optional<DeliveryLocation> findLast(Long deliveryId, Long viewerId) {
        deliveryApplicationService.requireParticipant(deliveryId, viewerId);
        return locationCache.findLast(deliveryId);
    }
}
