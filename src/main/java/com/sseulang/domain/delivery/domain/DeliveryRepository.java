package com.sseulang.domain.delivery.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Delivery Aggregate Repository. 도메인 layer 인터페이스 — Spring/JPA 의존 X.
 * 구현은 {@code domain/delivery/infrastructure/persistence}.
 */
public interface DeliveryRepository {

    DeliveryRequest save(DeliveryRequest delivery);

    Optional<DeliveryRequest> findById(Long id);

    /**
     * 라이더 수락 race 차단용 conditional UPDATE.
     *
     * <p>{@code UPDATE deliveries SET rider_id=?, status='수락', accepted_at=?
     * WHERE id=? AND status='모집중'} — 영향 행 수가 1 이면 수락 성공, 0 이면 race 패배
     * (이미 다른 라이더가 수락했거나 취소됨). 본인이 등록한 요청 차단은 ApplicationService 가
     * 호출 전 사전 검증.</p>
     *
     * @return 영향 행 수 (0 또는 1)
     */
    int acceptIfStillOpen(Long deliveryId, Long riderId, LocalDateTime acceptedAt);

    /** 모집중 상태의 페이징 목록 — 라이더가 수락 가능한 후보. */
    Page<DeliveryRequest> findOpenList(Pageable pageable);

    /** 사용자가 요청자 또는 라이더로 참여한 요청 페이징. */
    Page<DeliveryRequest> findByParticipant(Long userId, Pageable pageable);
}
