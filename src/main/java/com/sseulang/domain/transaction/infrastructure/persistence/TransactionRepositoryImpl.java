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
    public boolean existsActiveByChatRoomId(Long chatRoomId) {
        if (chatRoomId == null) {
            return false;
        }
        return jpa.existsActiveByChatRoomIdJpql(chatRoomId);
    }

    @Override
    public java.util.List<Transaction> findByItemIdOrderByIdDesc(Long itemId) {
        if (itemId == null) return java.util.List.of();
        return jpa.findByItemIdOrderByIdDesc(itemId);
    }

    @Override
    public Optional<Transaction> findByEscrowApplicationId(Long escrowApplicationId) {
        if (escrowApplicationId == null) return Optional.empty();
        return jpa.findByEscrowApplicationId(escrowApplicationId);
    }

    @Override
    public List<Transaction> findByEscrowApplicationIdIn(java.util.Collection<Long> escrowApplicationIds) {
        if (escrowApplicationIds == null || escrowApplicationIds.isEmpty()) return List.of();
        return jpa.findByEscrowApplicationIdIn(escrowApplicationIds);
    }

    @Override
    public Optional<Transaction> findLatestNonCanceledByChatRoomId(Long chatRoomId) {
        if (chatRoomId == null) return Optional.empty();
        List<Transaction> rows = jpa.findLatestNonCanceledByChatRoomIdJpql(
                chatRoomId, org.springframework.data.domain.PageRequest.of(0, 1));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<Transaction> findLatestNonCanceledByChatRoomIdIn(java.util.Collection<Long> chatRoomIds) {
        if (chatRoomIds == null || chatRoomIds.isEmpty()) return List.of();
        // 채팅방당 최신 1건만 — id desc 정렬 후 chatRoomId 첫 등장만 유지
        List<Transaction> all = jpa.findNonCanceledByChatRoomIdInJpql(chatRoomIds);
        all.sort(java.util.Comparator.comparing(Transaction::getId).reversed());
        java.util.Map<Long, Transaction> latest = new java.util.LinkedHashMap<>();
        for (Transaction t : all) latest.putIfAbsent(t.getChatRoomId(), t);
        return new java.util.ArrayList<>(latest.values());
    }

    @Override
    public List<TransactionStatusCount> countGroupByStatus() {
        return jpa.countGroupByStatusJpql();
    }

    @Override
    public List<com.sseulang.domain.transaction.domain.TransactionRepository.TradeTypeCount>
            countByTradeTypeBetween(java.time.LocalDateTime from, java.time.LocalDateTime to) {
        return jpa.countByTradeTypeBetweenJpql(from, to);
    }

    @Override
    public List<TransactionStatusCount> countByStatusBetween(java.time.LocalDateTime from, java.time.LocalDateTime to) {
        return jpa.countByStatusBetweenJpql(from, to);
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
    public Page<Transaction> adminSearch(
            java.time.LocalDateTime startDate,
            java.time.LocalDateTime endDate,
            com.sseulang.domain.item.domain.TradeType tradeType,
            TransactionStatus status,
            String keyword,
            java.util.Collection<Long> matchedUserIds,
            Pageable pageable
    ) {
        
        
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        Long keywordId = parseKeywordId(keyword);
        boolean hasUserIds = matchedUserIds != null && !matchedUserIds.isEmpty();
        if (hasKeyword && keywordId == null && !hasUserIds) {
            return new org.springframework.data.domain.PageImpl<>(java.util.Collections.emptyList(), pageable, 0);
        }
        java.util.Collection<Long> userIdsParam = hasUserIds ? matchedUserIds : java.util.List.of(0L); 
        return jpa.adminSearchJpql(startDate, endDate, tradeType, status, keywordId, hasUserIds, userIdsParam, pageable);
    }

    
    private static Long parseKeywordId(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        try {
            return Long.parseLong(keyword.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public List<com.sseulang.domain.transaction.domain.TransactionMonthlyStat> countCompletedMonthly(
            java.time.YearMonth from, java.time.YearMonth to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from/to 는 필수입니다");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from 은 to 이전이어야 합니다");
        }
        java.time.LocalDateTime fromTs = from.atDay(1).atStartOfDay();
        
        java.time.LocalDateTime toTs = to.plusMonths(1).atDay(1).atStartOfDay();
        return jpa.countCompletedMonthlyRaw(fromTs, toTs).stream()
                .map(r -> new com.sseulang.domain.transaction.domain.TransactionMonthlyStat(
                        java.time.YearMonth.of(r.getY(), r.getM()), r.getCnt(), r.getAmt()))
                .toList();
    }

    @Override
    public Page<Transaction> findPendingReviewable(Long userId, LocalDateTime since, Pageable pageable) {
        return jpa.findPendingReviewableJpql(userId, since, pageable);
    }

    @Override
    public Page<Transaction> findMyTransactions(
            Long userId, TransactionRole role, TransactionStatus status, Pageable pageable) {
        return findMyTransactions(userId, role,
                status == null ? java.util.List.of() : java.util.List.of(status), pageable);
    }

    @Override
    public java.util.List<Transaction> findActiveRentalsByItemId(Long itemId) {
        return jpa.findActiveRentalsByItemIdJpql(itemId);
    }

    @Override
    public java.util.Set<Long> findActiveRentalItemIds(java.util.Collection<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return java.util.Collections.emptySet();
        }
        java.util.List<Long> rows = jpa.findActiveRentalItemIdsJpql(itemIds);
        return new java.util.LinkedHashSet<>(rows == null ? java.util.List.of() : rows);
    }

    @Override
    public java.util.List<Transaction> findReturnRequestedBefore(LocalDateTime threshold) {
        return jpa.findReturnRequestedBeforeJpql(threshold);
    }

    @Override
    public java.util.List<Transaction> findActiveDirectByChatRoomId(Long chatRoomId) {
        if (chatRoomId == null) return java.util.List.of();
        return jpa.findActiveDirectByChatRoomIdJpql(chatRoomId);
    }

    @Override
    public Page<Transaction> findMyTransactions(
            Long userId, TransactionRole role,
            java.util.Collection<TransactionStatus> statuses, Pageable pageable) {
        boolean noFilter = statuses == null || statuses.isEmpty();
        boolean single = !noFilter && statuses.size() == 1;
        TransactionStatus only = single ? statuses.iterator().next() : null;

        if (role == null) {
            if (noFilter) return jpa.findByParticipant(userId, pageable);
            if (single) return jpa.findByParticipantAndStatus(userId, only, pageable);
            return jpa.findByParticipantAndStatusIn(userId, statuses, pageable);
        }
        if (role == TransactionRole.BUYER) {
            if (noFilter) return jpa.findByBuyer(userId, pageable);
            if (single) return jpa.findByBuyerAndStatus(userId, only, pageable);
            return jpa.findByBuyerAndStatusIn(userId, statuses, pageable);
        }
        if (noFilter) return jpa.findBySeller(userId, pageable);
        if (single) return jpa.findBySellerAndStatus(userId, only, pageable);
        return jpa.findBySellerAndStatusIn(userId, statuses, pageable);
    }
}
