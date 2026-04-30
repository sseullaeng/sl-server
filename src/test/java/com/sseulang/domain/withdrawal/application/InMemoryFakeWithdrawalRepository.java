package com.sseulang.domain.withdrawal.application;

import com.sseulang.domain.withdrawal.domain.Withdrawal;
import com.sseulang.domain.withdrawal.domain.WithdrawalRepository;
import com.sseulang.domain.withdrawal.domain.WithdrawalStatus;
import com.sseulang.domain.withdrawal.domain.WithdrawalStatusCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryFakeWithdrawalRepository implements WithdrawalRepository {

    private final Map<Long, Withdrawal> store = new HashMap<>();
    private long sequence = 0;

    @Override
    public Withdrawal save(Withdrawal withdrawal) {
        if (withdrawal.getId() == null) {
            ReflectionTestUtils.setField(withdrawal, "id", ++sequence);
        }
        store.put(withdrawal.getId(), withdrawal);
        return withdrawal;
    }

    @Override
    public Optional<Withdrawal> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Withdrawal> findByIdForUpdate(Long id) {
        // fake — 락 의미 X. prod 동시성 IT 에서 실제 PESSIMISTIC_WRITE 검증.
        return findById(id);
    }

    @Override
    public Optional<Withdrawal> findByIdAndUserIdForUpdate(Long id, Long userId) {
        return findById(id).filter(w -> userId != null && userId.equals(w.getUserId()));
    }

    @Override
    public Optional<Withdrawal> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey) {
        if (idempotencyKey == null) return Optional.empty();
        return store.values().stream()
                .filter(w -> userId.equals(w.getUserId()) && idempotencyKey.equals(w.getIdempotencyKey()))
                .findFirst();
    }

    @Override
    public Page<Withdrawal> findByUserId(Long userId, Pageable pageable) {
        List<Withdrawal> filtered = store.values().stream()
                .filter(w -> userId.equals(w.getUserId()))
                .sorted(Comparator.comparing(Withdrawal::getId).reversed())
                .toList();
        return new PageImpl<>(filtered, pageable, filtered.size());
    }

    @Override
    public Page<Withdrawal> findByStatus(WithdrawalStatus status, Pageable pageable) {
        List<Withdrawal> filtered = store.values().stream()
                .filter(w -> status == null || w.getStatus() == status)
                .sorted(Comparator.comparing(Withdrawal::getId).reversed())
                .toList();
        return new PageImpl<>(filtered, pageable, filtered.size());
    }

    @Override
    public List<WithdrawalStatusCount> countGroupByStatus() {
        return store.values().stream()
                .collect(java.util.stream.Collectors.groupingBy(Withdrawal::getStatus, java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .map(e -> new WithdrawalStatusCount(e.getKey(), e.getValue()))
                .toList();
    }

    @Override
    public long sumCompletedAmount() {
        return store.values().stream()
                .filter(w -> w.getStatus() == WithdrawalStatus.완료)
                .mapToLong(Withdrawal::getAmount)
                .sum();
    }
}
