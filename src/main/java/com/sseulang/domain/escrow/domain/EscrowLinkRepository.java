package com.sseulang.domain.escrow.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * EscrowLink 도메인 Repository (인터페이스).
 * 구현은 {@code infrastructure/persistence/} 의 JPA Adapter.
 */
public interface EscrowLinkRepository {

    EscrowLink save(EscrowLink link);

    Optional<EscrowLink> findById(Long id);

    Optional<EscrowLink> findByLinkToken(String linkToken);

    Page<EscrowLink> findByInitiatorId(Long initiatorId, Pageable pageable);

    /**
     * 첫 폼 제출자 = 수신자 확정 — atomic UPDATE (race-safe).
     * receiver_id IS NULL && status = 대기 && expires_at > NOW() && initiator_id != receiverId 조건 만족 시만 성공.
     *
     * @return 영향 받은 row 수 (0=race lose 또는 만료/본인, 1=성공)
     */
    int claimReceiverIfAvailable(Long linkId, Long receiverId);
}
