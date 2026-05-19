package com.sseulang.domain.escrow.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface EscrowLinkRepository {

    EscrowLink save(EscrowLink link);

    Optional<EscrowLink> findById(Long id);

    Optional<EscrowLink> findByLinkToken(String linkToken);

    Page<EscrowLink> findByInitiatorId(Long initiatorId, Pageable pageable);

    

    int claimReceiverIfAvailable(Long linkId, Long receiverId);
}
