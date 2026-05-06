package com.sseulang.domain.delivery.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Delivery Aggregate Repository. 도메인 layer 인터페이스 — Spring/JPA 의존 X.
 * 구현은 {@code domain/delivery/infrastructure/persistence}.
 */
public interface DeliveryRepository {

    DeliveryRequest save(DeliveryRequest delivery);

    Optional<DeliveryRequest> findById(Long id);

    /**
     * 정산({@code complete}) 진입 시 비관적 락 획득. 동시 complete 호출 race 를 직렬화.
     * (게이트 1 round 1 — Critical 1: 동시 complete 시 잔액 2회 변동 가능 회귀 차단.)
     */
    Optional<DeliveryRequest> findByIdForUpdate(Long id);

    /**
     * 라이더 수락 race 차단용 conditional UPDATE.
     *
     * <p>{@code UPDATE deliveries SET rider_id=?, status='수락', accepted_at=?
     * WHERE id=? AND status='모집중' AND requester_id <> ?} — 영향 행 수가 1 이면 수락 성공.
     * 0 이면 race 패배 / 취소됨 / 본인 거래 (게이트 1 round 1 — W-3: SQL 레벨에서도 본인 거래 차단).</p>
     *
     * @return 영향 행 수 (0 또는 1)
     */
    int acceptIfStillOpen(Long deliveryId, Long riderId, LocalDateTime acceptedAt);

    /**
     * 요청자 취소 race 차단용 conditional UPDATE.
     *
     * <p>{@code UPDATE deliveries SET status='취소', canceled_at=?, cancel_reason=?
     * WHERE id=? AND requester_id=? AND status='모집중'} — 영향 행 수가 1 이면 취소 성공,
     * 0 이면 이미 수락됐거나 (cancel vs accept race) 취소된 상태 / 본인 자원 아님.</p>
     *
     * <p>(게이트 1 round 1 — Critical 2: cancel 이 일반 로드 후 set 이라 accept conditional UPDATE
     * 결과를 덮어써 "수락 → 취소" race 발생하던 회귀 차단.)</p>
     *
     * @return 영향 행 수 (0 또는 1)
     */
    int cancelIfStillOpen(Long deliveryId, Long requesterId, LocalDateTime canceledAt, String reason);

    /** 모집중 상태의 페이징 목록 — 라이더가 수락 가능한 후보. */
    Page<DeliveryRequest> findOpenList(Pageable pageable);

    /** 사용자가 요청자 또는 라이더로 참여한 요청 페이징. */
    Page<DeliveryRequest> findByParticipant(Long userId, Pageable pageable);

    /** status 별 카운트 집계 (admin stats). 단일 GROUP BY — N+1 없음. */
    List<DeliveryStatusCount> countGroupByStatus();

    /** 정산완료 상태의 fee 총합 (admin stats). 누적 라이더 수익 추정. */
    long sumSettledFee();

    /** 거래대행 application 으로 자동 생성된 delivery 조회 — Escrow 정산 시 rider 식별용. */
    Optional<DeliveryRequest> findByEscrowApplicationId(Long escrowApplicationId);
}
