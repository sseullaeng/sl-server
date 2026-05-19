package com.sseulang.domain.delivery.infrastructure.persistence;

import com.sseulang.domain.delivery.domain.DeliveryRequest;
import com.sseulang.domain.delivery.domain.DeliveryStatus;
import com.sseulang.domain.delivery.domain.DeliveryStatusCount;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

interface DeliveryJpaRepository extends JpaRepository<DeliveryRequest, Long> {

    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DeliveryRequest d WHERE d.id = :id")
    Optional<DeliveryRequest> findByIdForUpdate(@Param("id") Long id);

    

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

    
    @Query("""
            SELECT d FROM DeliveryRequest d
             WHERE d.requesterId = :userId
                OR d.riderId = :userId
             ORDER BY d.requestedAt DESC
            """)
    Page<DeliveryRequest> findByParticipant(@Param("userId") Long userId, Pageable pageable);

    
    @Query("""
            SELECT new com.sseulang.domain.delivery.domain.DeliveryStatusCount(d.status, COUNT(d))
              FROM DeliveryRequest d
             GROUP BY d.status
            """)
    List<DeliveryStatusCount> countGroupByStatusJpql();

    
    @Query("""
            SELECT COALESCE(SUM(d.fee), 0)
              FROM DeliveryRequest d
             WHERE d.status = com.sseulang.domain.delivery.domain.DeliveryStatus.정산완료
            """)
    Long sumSettledFeeJpql();

    java.util.Optional<DeliveryRequest> findByEscrowApplicationId(Long escrowApplicationId);

    java.util.List<DeliveryRequest> findByEscrowApplicationIdIn(java.util.Collection<Long> escrowApplicationIds);

    java.util.Optional<DeliveryRequest> findByEscrowApplicationIdAndDirection(
            Long escrowApplicationId, com.sseulang.domain.delivery.domain.DeliveryDirection direction);

    @Query("""
            SELECT d FROM DeliveryRequest d
             WHERE (:status IS NULL OR d.status = :status)
               AND (:riderId IS NULL OR d.riderId = :riderId)
               AND (:requesterId IS NULL OR d.requesterId = :requesterId)
               AND (:createdAfter IS NULL OR d.requestedAt >= :createdAfter)
               AND (:createdBefore IS NULL OR d.requestedAt < :createdBefore)
            """)
    Page<DeliveryRequest> adminSearch(
            @Param("status") DeliveryStatus status,
            @Param("riderId") Long riderId,
            @Param("requesterId") Long requesterId,
            @Param("createdAfter") LocalDateTime createdAfter,
            @Param("createdBefore") LocalDateTime createdBefore,
            Pageable pageable
    );

    @Query("SELECT COUNT(d) FROM DeliveryRequest d WHERE d.requestedAt >= :since")
    long countCreatedSince(@Param("since") LocalDateTime since);
}
