package com.sseulang.domain.escrow.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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

    
    long countInProgress();
}
