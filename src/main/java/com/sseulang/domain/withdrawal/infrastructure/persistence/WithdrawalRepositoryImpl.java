package com.sseulang.domain.withdrawal.infrastructure.persistence;

import com.sseulang.domain.withdrawal.domain.Withdrawal;
import com.sseulang.domain.withdrawal.domain.WithdrawalRepository;
import com.sseulang.domain.withdrawal.domain.WithdrawalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class WithdrawalRepositoryImpl implements WithdrawalRepository {

    private final WithdrawalJpaRepository jpa;

    public WithdrawalRepositoryImpl(WithdrawalJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Withdrawal save(Withdrawal withdrawal) {
        return jpa.save(withdrawal);
    }

    @Override
    public Optional<Withdrawal> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<Withdrawal> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id);
    }

    @Override
    public Optional<Withdrawal> findByIdAndUserIdForUpdate(Long id, Long userId) {
        return jpa.findByIdAndUserIdForUpdate(id, userId);
    }

    @Override
    public Optional<Withdrawal> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey) {
        return jpa.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
    }

    @Override
    public Page<Withdrawal> findByUserId(Long userId, Pageable pageable) {
        return jpa.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Override
    public Page<Withdrawal> findByStatus(WithdrawalStatus status, Pageable pageable) {
        return status == null
                ? jpa.findAllByOrderByCreatedAtDesc(pageable)
                : jpa.findByStatusOrderByCreatedAtDesc(status, pageable);
    }
}
