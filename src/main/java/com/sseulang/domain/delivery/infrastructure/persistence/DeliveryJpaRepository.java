package com.sseulang.domain.delivery.infrastructure.persistence;

import com.sseulang.domain.delivery.domain.DeliveryRequest;
import com.sseulang.domain.delivery.domain.DeliveryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/** Spring Data JPA — {@link DeliveryRepositoryImpl} 가 wrapping. 외부 직접 import 금지. */
interface DeliveryJpaRepository extends JpaRepository<DeliveryRequest, Long> {

    /** 정산 진입 직렬화 — SELECT FOR UPDATE 로 동시 complete race 차단 (게이트 1 round 1). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DeliveryRequest d WHERE d.id = :id")
    Optional<DeliveryRequest> findByIdForUpdate(@Param("id") Long id);

    /**
     * 라이더 수락 race 차단 — conditional UPDATE.
     * <p>{@code WHERE status='모집중' AND requester_id <> riderId} 로 동시 두 라이더 중 하나만
     * 1 rows affected. SQL 자체에 본인 거래 차단 (이중 방어 — 게이트 1 round 1 W-3).</p>
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE DeliveryRequest d
               SET d.riderId = :riderId,
                   d.status = com.sseulang.domain.delivery.domain.DeliveryStatus.수락,
                   d.acceptedAt = :acceptedAt
             WHERE d.id = :id
               AND d.status = com.sseulang.domain.delivery.domain.DeliveryStatus.모집중
               AND d.requesterId <> :riderId
            """)
    int acceptIfStillOpen(@Param("id") Long id,
                          @Param("riderId") Long riderId,
                          @Param("acceptedAt") LocalDateTime acceptedAt);

    /**
     * 요청자 취소 race 차단 — conditional UPDATE. accept 와 동시 발생 시 한 쪽만 성공.
     * (게이트 1 round 1 — Critical 2.)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE DeliveryRequest d
               SET d.status = com.sseulang.domain.delivery.domain.DeliveryStatus.취소,
                   d.canceledAt = :canceledAt,
                   d.cancelReason = :reason
             WHERE d.id = :id
               AND d.requesterId = :requesterId
               AND d.status = com.sseulang.domain.delivery.domain.DeliveryStatus.모집중
            """)
    int cancelIfStillOpen(@Param("id") Long id,
                          @Param("requesterId") Long requesterId,
                          @Param("canceledAt") LocalDateTime canceledAt,
                          @Param("reason") String reason);

    Page<DeliveryRequest> findByStatusOrderByRequestedAtDesc(DeliveryStatus status, Pageable pageable);

    /** 요청자 또는 라이더로 참여한 요청. */
    @Query("""
            SELECT d FROM DeliveryRequest d
             WHERE d.requesterId = :userId
                OR d.riderId = :userId
             ORDER BY d.requestedAt DESC
            """)
    Page<DeliveryRequest> findByParticipant(@Param("userId") Long userId, Pageable pageable);
}
