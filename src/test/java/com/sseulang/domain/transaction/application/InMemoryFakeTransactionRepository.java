package com.sseulang.domain.transaction.application;

import com.sseulang.domain.transaction.domain.Transaction;
import com.sseulang.domain.transaction.domain.TransactionRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryFakeTransactionRepository implements TransactionRepository {

    private final Map<Long, Transaction> store = new HashMap<>();
    private long sequence = 0;

    @Override
    public Optional<Transaction> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Transaction> findByIdForUpdate(Long id) {
        // fake — 락 의미 없음. 실제 동시성 검증은 IT 에서.
        return findById(id);
    }

    @Override
    public Transaction save(Transaction transaction) {
        if (transaction.getId() == null) {
            ReflectionTestUtils.setField(transaction, "id", ++sequence);
        }
        store.put(transaction.getId(), transaction);
        return transaction;
    }
}
