package com.sseulang.domain.transaction.infrastructure.persistence;

import com.sseulang.domain.transaction.application.dto.TransactionRole;
import com.sseulang.domain.transaction.domain.Transaction;
import com.sseulang.domain.transaction.domain.TransactionRepository;
import com.sseulang.domain.transaction.domain.TransactionStatus;
import com.sseulang.domain.transaction.domain.TransactionStatusCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class TransactionRepositoryImpl implements TransactionRepository {

    private final TransactionJpaRepository jpa;

    public TransactionRepositoryImpl(TransactionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Transaction> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<Transaction> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id);
    }

    @Override
    public Transaction save(Transaction transaction) {
        return jpa.save(transaction);
    }

    @Override
    public List<TransactionStatusCount> countGroupByStatus() {
        return jpa.countGroupByStatusJpql();
    }

    @Override
    public java.util.Map<Long, Long> countByUserIdsAsParticipant(java.util.Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        java.util.Map<Long, Long> result = new java.util.HashMap<>();
        for (TransactionJpaRepository.UserCountRow row : jpa.countAsSellerRaw(userIds)) {
            result.merge(row.getUserId(), row.getCnt(), Long::sum);
        }
        for (TransactionJpaRepository.UserCountRow row : jpa.countAsBuyerRaw(userIds)) {
            result.merge(row.getUserId(), row.getCnt(), Long::sum);
        }
        return result;
    }

    @Override
    public Page<Transaction> findPendingReviewable(Long userId, LocalDateTime since, Pageable pageable) {
        return jpa.findPendingReviewableJpql(userId, since, pageable);
    }

    @Override
    public Page<Transaction> findMyTransactions(
            Long userId, TransactionRole role, TransactionStatus status, Pageable pageable) {
        if (role == null) {
            return (status == null)
                    ? jpa.findByParticipant(userId, pageable)
                    : jpa.findByParticipantAndStatus(userId, status, pageable);
        }
        if (role == TransactionRole.BUYER) {
            return (status == null)
                    ? jpa.findByBuyer(userId, pageable)
                    : jpa.findByBuyerAndStatus(userId, status, pageable);
        }
        return (status == null)
                ? jpa.findBySeller(userId, pageable)
                : jpa.findBySellerAndStatus(userId, status, pageable);
    }
}
