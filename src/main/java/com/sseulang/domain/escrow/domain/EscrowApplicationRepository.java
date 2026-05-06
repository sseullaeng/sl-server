package com.sseulang.domain.escrow.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface EscrowApplicationRepository {

    EscrowApplication save(EscrowApplication application);

    Optional<EscrowApplication> findById(Long id);

    Optional<EscrowApplication> findByLinkId(Long linkId);

    /** 본인이 참여한 (initiator OR receiver) application 목록. */
    Page<EscrowApplication> findMyApplications(Long userId, Pageable pageable);

    /** Admin — 전체. */
    Page<EscrowApplication> findAll(Pageable pageable);

    /** Admin — 상태 필터. */
    Page<EscrowApplication> findAllByStatus(EscrowApplicationStatus status, Pageable pageable);

    /**
     * Passive timeout 검증 — 결제대기 + payment_due_at 경과한 것들. 사용자 조회/admin 시점에 사용.
     */
    List<EscrowApplication> findPaymentTimedOut();

    /** 진행 중 N건 (admin fee_settings 변경 시 영향 안내용). 결정 #12 12-a HH2. */
    long countInProgress();
}
