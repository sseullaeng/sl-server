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

    // 라운드 12 — admin item 상세에서 거래 이력 조회 (최신순).
    java.util.List<Transaction> findByItemIdOrderByIdDesc(Long itemId);

    // 라운드 12 — 거래대행 paired 매핑 (1:1, UNIQUE).
    Optional<Transaction> findByEscrowApplicationId(Long escrowApplicationId);

    java.util.List<Transaction> findByEscrowApplicationIdIn(java.util.Collection<Long> escrowApplicationIds);

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

    // multi-status — null/empty = 전체. 거래 많은 사용자가 여러 상태(예: 채팅중,예약,인계완료) 조회.
    Page<Transaction> findMyTransactions(
            Long userId, TransactionRole role,
            java.util.Collection<TransactionStatus> statuses, Pageable pageable);

    // B-2: 대여 달력 — 활성(취소/거래완료 제외) 대여 거래의 rentalStart/End 페어. 같은 item 의 예약 충돌 검사용.
    List<Transaction> findActiveRentalsByItemId(Long itemId);

    // 아이템 카드용 — 현재 대여 흐름이 살아있는 item id 배치 조회.
    java.util.Set<Long> findActiveRentalItemIds(java.util.Collection<Long> itemIds);

    // B-6: 7일 자동 완료 스케줄러 — 반납요청 상태 + returnRequestedAt < threshold.
    List<Transaction> findReturnRequestedBefore(LocalDateTime threshold);

    // B-5: escrow 종료 시 cascade 후보 — 같은 chatRoom 의 직거래(non-paired) 활성 tx.
    List<Transaction> findActiveDirectByChatRoomId(Long chatRoomId);
}
