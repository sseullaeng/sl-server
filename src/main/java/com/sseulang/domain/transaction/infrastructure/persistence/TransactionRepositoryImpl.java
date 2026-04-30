package com.sseulang.domain.transaction.infrastructure.persistence;

import com.sseulang.domain.transaction.domain.Transaction;
import com.sseulang.domain.transaction.domain.TransactionRepository;
import com.sseulang.domain.transaction.domain.TransactionStatusCount;
import org.springframework.stereotype.Repository;

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
}
