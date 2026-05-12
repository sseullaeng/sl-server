package com.sseulang.domain.transaction.domain;

import com.sseulang.domain.transaction.application.dto.TransactionRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository {

    Optional<Transaction> findById(Long id);

    

    Optional<Transaction> findByIdForUpdate(Long id);

    Transaction save(Transaction transaction);

    

    boolean existsActiveByChatRoomId(Long chatRoomId);

    // 라운드 12 — 채팅방 카드용. 비취소 최신 1건 (취소 제외, 거래완료까지 포함).
    Optional<Transaction> findLatestNonCanceledByChatRoomId(Long chatRoomId);

    java.util.List<Transaction> findLatestNonCanceledByChatRoomIdIn(java.util.Collection<Long> chatRoomIds);

    

    
    List<TransactionStatusCount> countGroupByStatus();

    

    List<TradeTypeCount> countByTradeTypeBetween(LocalDateTime from, LocalDateTime to);

    

    List<TransactionStatusCount> countByStatusBetween(LocalDateTime from, LocalDateTime to);

    
    record TradeTypeCount(com.sseulang.domain.item.domain.TradeType tradeType, long count) { }

    

    java.util.Map<Long, Long> countByUserIdsAsParticipant(java.util.Collection<Long> userIds);

    

    List<TransactionMonthlyStat> countCompletedMonthly(java.time.YearMonth from, java.time.YearMonth to);

    

    org.springframework.data.domain.Page<Transaction> adminSearch(
            java.time.LocalDateTime startDate,
            java.time.LocalDateTime endDate,
            com.sseulang.domain.item.domain.TradeType tradeType,
            TransactionStatus status,
            String keyword,
            java.util.Collection<Long> matchedUserIds,
            org.springframework.data.domain.Pageable pageable
    );

    

    

    Page<Transaction> findPendingReviewable(Long userId, LocalDateTime since, Pageable pageable);

    

    Page<Transaction> findMyTransactions(
            Long userId, TransactionRole role, TransactionStatus status, Pageable pageable);
}
