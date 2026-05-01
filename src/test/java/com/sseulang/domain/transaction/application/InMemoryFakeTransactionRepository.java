package com.sseulang.domain.transaction.application;

import com.sseulang.domain.transaction.domain.Transaction;
import com.sseulang.domain.transaction.domain.TransactionRepository;
import com.sseulang.domain.transaction.domain.TransactionStatus;
import com.sseulang.domain.transaction.domain.TransactionStatusCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Override
    public List<TransactionStatusCount> countGroupByStatus() {
        return store.values().stream()
                .collect(Collectors.groupingBy(Transaction::getStatus, Collectors.counting()))
                .entrySet().stream()
                .map(e -> new TransactionStatusCount(e.getKey(), e.getValue()))
                .toList();
    }

    /**
     * 단위 테스트용 — Review 도메인을 cross-aggregate 로 알 수 없으므로 외부에서 (txId, reviewerId) pair 를
     * 주입받아 NOT EXISTS 시뮬레이션. 비어 있으면 모든 거래완료 거래가 pending 으로 반환됨.
     */
    private final Set<String> reviewedPairs = new HashSet<>();

    public void markReviewed(Long transactionId, Long reviewerId) {
        reviewedPairs.add(transactionId + ":" + reviewerId);
    }

    @Override
    public Page<Transaction> findPendingReviewable(Long userId, LocalDateTime since, Pageable pageable) {
        List<Transaction> filtered = store.values().stream()
                .filter(t -> t.getStatus() == TransactionStatus.거래완료)
                .filter(t -> t.getCompletedAt() != null && !t.getCompletedAt().isBefore(since))
                .filter(t -> userId.equals(t.getSellerId()) || userId.equals(t.getBuyerId()))
                .filter(t -> !reviewedPairs.contains(t.getId() + ":" + userId))
                .sorted(Comparator.comparing(Transaction::getCompletedAt).reversed())
                .toList();
        int start = Math.min((int) pageable.getOffset(), filtered.size());
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
    }
}
