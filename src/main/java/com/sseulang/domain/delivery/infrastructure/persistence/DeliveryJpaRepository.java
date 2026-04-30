package com.sseulang.domain.delivery.infrastructure.persistence;

import com.sseulang.domain.delivery.domain.DeliveryRequest;
import com.sseulang.domain.delivery.domain.DeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/** Spring Data JPA — {@link DeliveryRepositoryImpl} 가 wrapping. 외부 직접 import 금지. */
interface DeliveryJpaRepository extends JpaRepository<DeliveryRequest, Long> {

    /**
     * 라이더 수락 race 차단 — conditional UPDATE.
     * <p>{@code WHERE status='모집중'} 조건으로 동시에 들어온 두 라이더 중 하나만 1 rows affected.
     * 이미 수락됐거나 취소된 요청은 0 rows. clearAutomatically 로 영속성 컨텍스트 동기화.</p>
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE DeliveryRequest d
               SET d.riderId = :riderId,
                   d.status = com.sseulang.domain.delivery.domain.DeliveryStatus.수락,
                   d.acceptedAt = :acceptedAt
             WHERE d.id = :id
               AND d.status = com.sseulang.domain.delivery.domain.DeliveryStatus.모집중
            """)
    int acceptIfStillOpen(@Param("id") Long id,
                          @Param("riderId") Long riderId,
                          @Param("acceptedAt") LocalDateTime acceptedAt);

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
