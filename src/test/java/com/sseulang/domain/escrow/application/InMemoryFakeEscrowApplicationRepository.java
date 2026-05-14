package com.sseulang.domain.escrow.application;

import com.sseulang.domain.escrow.domain.EscrowApplication;
import com.sseulang.domain.escrow.domain.EscrowApplicationRepository;
import com.sseulang.domain.escrow.domain.EscrowApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class InMemoryFakeEscrowApplicationRepository implements EscrowApplicationRepository {

    private final Map<Long, EscrowApplication> store = new HashMap<>();
    private long sequence = 0;

    @Override
    public EscrowApplication save(EscrowApplication application) {
        if (application.getId() == null) {
            ReflectionTestUtils.setField(application, "id", ++sequence);
            ReflectionTestUtils.setField(application, "createdAt", LocalDateTime.now());
        }
        ReflectionTestUtils.setField(application, "updatedAt", LocalDateTime.now());
        store.put(application.getId(), application);
        return application;
    }

    @Override
    public Optional<EscrowApplication> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<EscrowApplication> findByIdForUpdate(Long id) {
        // fake — 락 의미 없음. 실 동시성은 IT 에서.
        return findById(id);
    }

    @Override
    public Optional<EscrowApplication> findByLinkId(Long linkId) {
        return store.values().stream().filter(a -> a.getLinkId().equals(linkId)).findFirst();
    }

    @Override
    public Page<EscrowApplication> findMyApplications(Long userId, Pageable pageable) {
        List<EscrowApplication> list = store.values().stream()
                .filter(a -> a.getInitiatorId().equals(userId) || a.getReceiverId().equals(userId))
                .collect(Collectors.toList());
        return new PageImpl<>(list, pageable, list.size());
    }

    @Override
    public Page<EscrowApplication> findAll(Pageable pageable) {
        return new PageImpl<>(List.copyOf(store.values()), pageable, store.size());
    }

    @Override
    public Page<EscrowApplication> findAllByStatus(EscrowApplicationStatus status, Pageable pageable) {
        List<EscrowApplication> list = store.values().stream()
                .filter(a -> a.getStatus() == status)
                .collect(Collectors.toList());
        return new PageImpl<>(list, pageable, list.size());
    }

    @Override
    public List<EscrowApplication> findPaymentTimedOut() {
        return store.values().stream()
                .filter(EscrowApplication::isPaymentTimedOut)
                .collect(Collectors.toList());
    }

    @Override
    public List<EscrowApplication> findOverdueRentalEndApplications(LocalDateTime threshold) {
        return store.values().stream()
                .filter(EscrowApplication::isRentalMode)
                .filter(a -> a.getStatus() == EscrowApplicationStatus.사용중)
                .filter(a -> a.getRentalEndAt() != null && a.getRentalEndAt().isBefore(threshold))
                .sorted(java.util.Comparator.comparing(EscrowApplication::getRentalEndAt))
                .collect(Collectors.toList());
    }

    @Override
    public long countInProgress() {
        return store.values().stream()
                .filter(a -> a.getStatus() != EscrowApplicationStatus.완료 && a.getStatus() != EscrowApplicationStatus.취소)
                .count();
    }

    @Override
    public java.util.Optional<com.sseulang.domain.escrow.domain.EscrowApplication> findLatestNonCanceledByChatRoomId(Long chatRoomId) {
        if (chatRoomId == null) return java.util.Optional.empty();
        return store.values().stream()
                .filter(a -> chatRoomId.equals(a.getChatRoomId()) && a.getStatus() != EscrowApplicationStatus.취소)
                .max(java.util.Comparator.comparing(com.sseulang.domain.escrow.domain.EscrowApplication::getId));
    }

    @Override
    public java.util.List<com.sseulang.domain.escrow.domain.EscrowApplication> findLatestNonCanceledByChatRoomIdIn(java.util.Collection<Long> chatRoomIds) {
        if (chatRoomIds == null || chatRoomIds.isEmpty()) return java.util.List.of();
        java.util.Map<Long, com.sseulang.domain.escrow.domain.EscrowApplication> latest = new java.util.LinkedHashMap<>();
        store.values().stream()
                .filter(a -> a.getChatRoomId() != null && chatRoomIds.contains(a.getChatRoomId())
                        && a.getStatus() != EscrowApplicationStatus.취소)
                .sorted(java.util.Comparator.comparing(com.sseulang.domain.escrow.domain.EscrowApplication::getId).reversed())
                .forEach(a -> latest.putIfAbsent(a.getChatRoomId(), a));
        return new java.util.ArrayList<>(latest.values());
    }
}
