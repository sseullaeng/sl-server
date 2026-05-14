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
    public Optional<EscrowApplication> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id);
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
    public List<EscrowApplication> findOverdueRentalEndApplications(LocalDateTime threshold) {
        return jpa.findOverdueRentalEndApplications(threshold);
    }

    @Override
    public List<EscrowApplication> findOverdueCandidates(LocalDateTime cutoff) {
        return jpa.findOverdueCandidates(cutoff);
    }

    @Override
    public long countInProgress() {
        return jpa.countInProgress();
    }

    @Override
    public Optional<EscrowApplication> findLatestNonCanceledByChatRoomId(Long chatRoomId) {
        if (chatRoomId == null) return Optional.empty();
        List<EscrowApplication> rows = jpa.findLatestNonCanceledByChatRoomIdJpql(
                chatRoomId, org.springframework.data.domain.PageRequest.of(0, 1));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<EscrowApplication> findLatestNonCanceledByChatRoomIdIn(java.util.Collection<Long> chatRoomIds) {
        if (chatRoomIds == null || chatRoomIds.isEmpty()) return List.of();
        List<EscrowApplication> all = jpa.findNonCanceledByChatRoomIdInJpql(chatRoomIds);
        all.sort(java.util.Comparator.comparing(EscrowApplication::getId).reversed());
        java.util.Map<Long, EscrowApplication> latest = new java.util.LinkedHashMap<>();
        for (EscrowApplication a : all) latest.putIfAbsent(a.getChatRoomId(), a);
        return new java.util.ArrayList<>(latest.values());
    }
}
