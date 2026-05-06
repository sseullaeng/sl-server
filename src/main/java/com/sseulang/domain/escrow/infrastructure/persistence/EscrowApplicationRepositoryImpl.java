package com.sseulang.domain.escrow.infrastructure.persistence;

import com.sseulang.domain.escrow.domain.EscrowApplication;
import com.sseulang.domain.escrow.domain.EscrowApplicationRepository;
import com.sseulang.domain.escrow.domain.EscrowApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class EscrowApplicationRepositoryImpl implements EscrowApplicationRepository {

    private final EscrowApplicationJpaRepository jpa;

    public EscrowApplicationRepositoryImpl(EscrowApplicationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public EscrowApplication save(EscrowApplication application) {
        return jpa.save(application);
    }

    @Override
    public Optional<EscrowApplication> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<EscrowApplication> findByLinkId(Long linkId) {
        return jpa.findByLinkId(linkId);
    }

    @Override
    public Page<EscrowApplication> findMyApplications(Long userId, Pageable pageable) {
        return jpa.findMyApplications(userId, pageable);
    }

    @Override
    public Page<EscrowApplication> findAll(Pageable pageable) {
        return jpa.findAll(pageable);
    }

    @Override
    public Page<EscrowApplication> findAllByStatus(EscrowApplicationStatus status, Pageable pageable) {
        return jpa.findAllByStatus(status, pageable);
    }

    @Override
    public List<EscrowApplication> findPaymentTimedOut() {
        return jpa.findPaymentTimedOut(LocalDateTime.now());
    }

    @Override
    public long countInProgress() {
        return jpa.countInProgress();
    }
}
