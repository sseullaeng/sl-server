package com.sseulang.domain.transaction.application;

import com.sseulang.domain.transaction.application.dto.TransactionRole;
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
    public boolean existsActiveByChatRoomId(Long chatRoomId) {
        if (chatRoomId == null) return false;
        return store.values().stream()
                .anyMatch(t -> chatRoomId.equals(t.getChatRoomId())
                        && t.getStatus() != TransactionStatus.거래완료
                        && t.getStatus() != TransactionStatus.취소);
    }

    @Override
    public java.util.Optional<Transaction> findByEscrowApplicationId(Long escrowApplicationId) {
        if (escrowApplicationId == null) return java.util.Optional.empty();
        return store.values().stream()
                .filter(t -> escrowApplicationId.equals(t.getEscrowApplicationId()))
                .findFirst();
    }

    @Override
    public java.util.List<Transaction> findByEscrowApplicationIdIn(java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return java.util.List.of();
        return store.values().stream()
                .filter(t -> t.getEscrowApplicationId() != null && ids.contains(t.getEscrowApplicationId()))
                .toList();
    }

    @Override
    public java.util.Optional<Transaction> findLatestNonCanceledByChatRoomId(Long chatRoomId) {
        if (chatRoomId == null) return java.util.Optional.empty();
        return store.values().stream()
                .filter(t -> chatRoomId.equals(t.getChatRoomId()) && t.getStatus() != TransactionStatus.취소)
                .max(java.util.Comparator.comparing(Transaction::getId));
    }

    @Override
    public java.util.List<Transaction> findLatestNonCanceledByChatRoomIdIn(java.util.Collection<Long> chatRoomIds) {
        if (chatRoomIds == null || chatRoomIds.isEmpty()) return java.util.List.of();
        java.util.Map<Long, Transaction> latest = new java.util.LinkedHashMap<>();
        store.values().stream()
                .filter(t -> t.getChatRoomId() != null && chatRoomIds.contains(t.getChatRoomId())
                        && t.getStatus() != TransactionStatus.취소)
                .sorted(java.util.Comparator.comparing(Transaction::getId).reversed())
                .forEach(t -> latest.putIfAbsent(t.getChatRoomId(), t));
        return new java.util.ArrayList<>(latest.values());
    }

    @Override
    public List<TransactionStatusCount> countGroupByStatus() {
        return store.values().stream()
                .collect(Collectors.groupingBy(Transaction::getStatus, Collectors.counting()))
                .entrySet().stream()
                .map(e -> new TransactionStatusCount(e.getKey(), e.getValue()))
                .toList();
    }

    @Override
    public List<com.sseulang.domain.transaction.domain.TransactionRepository.TradeTypeCount>
            countByTradeTypeBetween(java.time.LocalDateTime from, java.time.LocalDateTime to) {
        return store.values().stream()
                .filter(t -> withinRange(t.getCreatedAt(), from, to))
                .collect(Collectors.groupingBy(Transaction::getTradeType, Collectors.counting()))
                .entrySet().stream()
                .map(e -> new com.sseulang.domain.transaction.domain.TransactionRepository.TradeTypeCount(e.getKey(), e.getValue()))
                .toList();
    }

    @Override
    public List<TransactionStatusCount> countByStatusBetween(java.time.LocalDateTime from, java.time.LocalDateTime to) {
        return store.values().stream()
                .filter(t -> withinRange(t.getCreatedAt(), from, to))
                .collect(Collectors.groupingBy(Transaction::getStatus, Collectors.counting()))
                .entrySet().stream()
                .map(e -> new TransactionStatusCount(e.getKey(), e.getValue()))
                .toList();
    }

    private static boolean withinRange(java.time.LocalDateTime when, java.time.LocalDateTime from, java.time.LocalDateTime to) {
        return when != null && !when.isBefore(from) && when.isBefore(to);
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
    public Page<Transaction> findMyTransactions(
            Long userId, TransactionRole role, TransactionStatus status, Pageable pageable) {
        List<Transaction> filtered = store.values().stream()
                .filter(t -> {
                    if (role == null) {
                        return userId.equals(t.getSellerId()) || userId.equals(t.getBuyerId());
                    }
                    if (role == TransactionRole.BUYER) {
                        return userId.equals(t.getBuyerId());
                    }
                    return userId.equals(t.getSellerId());
                })
                .filter(t -> status == null || t.getStatus() == status)
                .sorted(Comparator.comparingLong(Transaction::getId).reversed())
                .toList();
        int start = Math.min((int) pageable.getOffset(), filtered.size());
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
    }

    @Override
    public java.util.List<com.sseulang.domain.transaction.domain.TransactionMonthlyStat> countCompletedMonthly(
            java.time.YearMonth from, java.time.YearMonth to) {
        Map<java.time.YearMonth, long[]> bucket = new HashMap<>();  // [count, amount]
        for (Transaction t : store.values()) {
            if (t.getStatus() != TransactionStatus.거래완료 || t.getCompletedAt() == null) continue;
            java.time.YearMonth m = java.time.YearMonth.from(t.getCompletedAt());
            if (m.isBefore(from) || m.isAfter(to)) continue;
            long[] arr = bucket.computeIfAbsent(m, k -> new long[]{0, 0});
            arr[0]++;
            arr[1] += t.getPrice();
        }
        return bucket.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new com.sseulang.domain.transaction.domain.TransactionMonthlyStat(
                        e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();
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

    @Override
    public Map<Long, Long> countByUserIdsAsParticipant(java.util.Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        Set<Long> idSet = new HashSet<>(userIds);
        Map<Long, Long> result = new HashMap<>();
        for (Transaction t : store.values()) {
            if (idSet.contains(t.getSellerId())) result.merge(t.getSellerId(), 1L, Long::sum);
            if (idSet.contains(t.getBuyerId())) result.merge(t.getBuyerId(), 1L, Long::sum);
        }
        return result;
    }

    @Override
    public Page<Transaction> adminSearch(
            LocalDateTime startDate,
            LocalDateTime endDate,
            com.sseulang.domain.item.domain.TradeType tradeType,
            TransactionStatus status,
            String keyword,
            java.util.Collection<Long> matchedUserIds,
            Pageable pageable
    ) {
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        Long keywordId = null;
        if (hasKeyword) {
            try { keywordId = Long.parseLong(keyword.trim()); } catch (NumberFormatException ignored) { }
        }
        boolean hasUserIds = matchedUserIds != null && !matchedUserIds.isEmpty();
        if (hasKeyword && keywordId == null && !hasUserIds) {
            return new PageImpl<>(java.util.Collections.emptyList(), pageable, 0);
        }
        final Long kId = keywordId;
        final Set<Long> userIdSet = hasUserIds ? new HashSet<>(matchedUserIds) : Set.of();
        List<Transaction> filtered = store.values().stream()
                .filter(t -> startDate == null || t.getCreatedAt() == null || !t.getCreatedAt().isBefore(startDate))
                .filter(t -> endDate == null || t.getCreatedAt() == null || !t.getCreatedAt().isAfter(endDate))
                .filter(t -> tradeType == null || t.getTradeType() == tradeType)
                .filter(t -> status == null || t.getStatus() == status)
                .filter(t -> {
                    // keyword 가 없으면 패스. 있으면 keywordId 매칭 OR userId IN.
                    if (kId == null && !hasUserIds) return true;
                    if (kId != null && (kId.equals(t.getId()) || kId.equals(t.getItemId()))) return true;
                    if (hasUserIds && (userIdSet.contains(t.getSellerId()) || userIdSet.contains(t.getBuyerId()))) return true;
                    return false;
                })
                .sorted(Comparator.comparingLong(Transaction::getId).reversed())
                .toList();
        int start = Math.min((int) pageable.getOffset(), filtered.size());
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
    }
}
