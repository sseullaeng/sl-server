package com.sseulang.domain.escrow.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EscrowApplicationRepository {

    EscrowApplication save(EscrowApplication application);

    Optional<EscrowApplication> findById(Long id);

    

    Optional<EscrowApplication> findByIdForUpdate(Long id);

    Optional<EscrowApplication> findByLinkId(Long linkId);

    
    Page<EscrowApplication> findMyApplications(Long userId, Pageable pageable);

    
    Page<EscrowApplication> findAll(Pageable pageable);

    
    Page<EscrowApplication> findAllByStatus(EscrowApplicationStatus status, Pageable pageable);

    

    List<EscrowApplication> findPaymentTimedOut();

    List<EscrowApplication> findOverdueRentalEndApplications(LocalDateTime threshold);

    
    long countInProgress();

    // 라운드 12 — 채팅방 카드용. INTERNAL 거래의 비취소 최신 1건.
    Optional<EscrowApplication> findLatestNonCanceledByChatRoomId(Long chatRoomId);

    java.util.List<EscrowApplication> findLatestNonCanceledByChatRoomIdIn(java.util.Collection<Long> chatRoomIds);
}
