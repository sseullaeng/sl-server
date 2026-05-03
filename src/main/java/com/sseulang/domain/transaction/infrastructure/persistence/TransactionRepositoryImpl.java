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
    public Page<Transaction> adminSearch(
            java.time.LocalDateTime startDate,
            java.time.LocalDateTime endDate,
            com.sseulang.domain.item.domain.TradeType tradeType,
            TransactionStatus status,
            String keyword,
            Pageable pageable
    ) {
        // keyword 입력이 있으면 숫자만 매칭 — 비숫자 (영문/이메일 등) 는 빈 결과 반환 (Codex round 9 hotfix).
        // null/blank 는 필터 미적용. NUMBER 매칭만이 현 범위 — LIKE 는 follow-up.
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        Long keywordId = parseKeywordId(keyword);
        if (hasKeyword && keywordId == null) {
            return new org.springframework.data.domain.PageImpl<>(java.util.Collections.emptyList(), pageable, 0);
        }
        return jpa.adminSearchJpql(startDate, endDate, tradeType, status, keywordId, pageable);
    }

    /** keyword 가 숫자면 long, 아니면 null. */
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
        // toTs 는 (to.다음월 1일 00:00) — exclusive 경계로 to 월 마지막 순간까지 포함.
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
