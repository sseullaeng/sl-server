package com.sseulang.domain.delivery.domain;

import java.util.Optional;

/**
 * 배달의 마지막 위치를 휘발 저장하는 캐시 포트. 구현은 Redis 어댑터.
 *
 * <p>설계:</p>
 * <ul>
 *   <li>key: {@code delivery:loc:{deliveryId}}</li>
 *   <li>TTL: 30분 (배달 시간 ≪ 30분 가정. 정산완료 후 자동 만료)</li>
 *   <li>마지막 위치 1건만 — history X (DB 비저장 정책, 가이드 §4.13)</li>
 * </ul>
 */
public interface DeliveryLocationCache {

    /** 마지막 위치 저장 (덮어쓰기). TTL 갱신. */
    void save(Long deliveryId, DeliveryLocation location);

    /** 마지막 위치 조회. 미존재 / TTL 만료 시 빈 Optional. */
    Optional<DeliveryLocation> findLast(Long deliveryId);

    /** 강제 만료 — 정산완료 / 취소 시 즉시 정리 (선택). */
    void evict(Long deliveryId);
}
