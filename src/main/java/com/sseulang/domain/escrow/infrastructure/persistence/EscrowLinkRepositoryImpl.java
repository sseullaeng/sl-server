package com.sseulang.domain.escrow.infrastructure.persistence;

import com.sseulang.domain.escrow.domain.EscrowLink;
import com.sseulang.domain.escrow.domain.EscrowLinkRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class EscrowLinkRepositoryImpl implements EscrowLinkRepository {

    private final EscrowLinkJpaRepository jpa;

    public EscrowLinkRepositoryImpl(EscrowLinkJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public EscrowLink save(EscrowLink link) {
        return jpa.save(link);
    }

    @Override
    public Optional<EscrowLink> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<EscrowLink> findByLinkToken(String linkToken) {
        return jpa.findByLinkToken(linkToken);
    }

    @Override
    public Page<EscrowLink> findByInitiatorId(Long initiatorId, Pageable pageable) {
        return jpa.findByInitiatorId(initiatorId, pageable);
    }

    @Override
    public int claimReceiverIfAvailable(Long linkId, Long receiverId) {
        return jpa.claimReceiverIfAvailable(linkId, receiverId, LocalDateTime.now());
    }
}
