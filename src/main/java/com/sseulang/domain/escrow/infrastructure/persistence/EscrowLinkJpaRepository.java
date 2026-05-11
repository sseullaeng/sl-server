package com.sseulang.domain.escrow.infrastructure.persistence;

import com.sseulang.domain.escrow.domain.EscrowLink;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EscrowLinkJpaRepository extends JpaRepository<EscrowLink, Long> {

    Optional<EscrowLink> findByLinkToken(String linkToken);

    Page<EscrowLink> findByInitiatorId(Long initiatorId, Pageable pageable);

    

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE EscrowLink l
               SET l.receiverId = :receiverId
             WHERE l.id = :linkId
               AND l.receiverId IS NULL
               AND l.status = com.sseulang.domain.escrow.domain.EscrowLinkStatus.대기
               AND l.expiresAt > :now
               AND l.initiatorId <> :receiverId
            """)
    int claimReceiverIfAvailable(
            @Param("linkId") Long linkId,
            @Param("receiverId") Long receiverId,
            @Param("now") LocalDateTime now
    );
}
