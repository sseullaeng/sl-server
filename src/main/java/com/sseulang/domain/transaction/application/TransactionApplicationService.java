package com.sseulang.domain.transaction.application;

import com.sseulang.domain.chat.application.ChatRoomApplicationService;
import com.sseulang.domain.item.application.ItemApplicationService;
import com.sseulang.domain.item.application.dto.ItemForTransactionResult;
import com.sseulang.domain.point.application.PointApplicationService;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.domain.transaction.application.dto.PendingReviewableResult;
import com.sseulang.domain.transaction.application.dto.ReviewableTransactionResult;
import com.sseulang.domain.transaction.application.dto.TransactionCreateCommand;
import com.sseulang.domain.transaction.application.dto.TransactionResult;
import com.sseulang.domain.transaction.application.dto.TransactionRole;
import com.sseulang.domain.transaction.application.dto.TransactionStatsResult;
import com.sseulang.domain.transaction.domain.Transaction;
import com.sseulang.domain.transaction.domain.TransactionRepository;
import com.sseulang.domain.transaction.domain.TransactionStatus;
import com.sseulang.domain.transaction.domain.TransactionStatusCount;
import com.sseulang.domain.transaction.domain.event.TransactionCanceledEvent;
import com.sseulang.domain.transaction.domain.event.TransactionHandoverConfirmedEvent;
import com.sseulang.domain.transaction.domain.event.TransactionReceiveConfirmedEvent;
import com.sseulang.domain.transaction.domain.event.TransactionReservedEvent;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class TransactionApplicationService {

    private final TransactionRepository transactionRepository;
    private final ItemApplicationService itemApplicationService;
    private final PointApplicationService pointApplicationService;
    private final UserApplicationService userApplicationService;
    private final ChatRoomApplicationService chatRoomApplicationService;
    private final ApplicationEventPublisher eventPublisher;
    private final java.time.Clock clock;

    public TransactionApplicationService(
            TransactionRepository transactionRepository,
            ItemApplicationService itemApplicationService,
            PointApplicationService pointApplicationService,
            UserApplicationService userApplicationService,
            ChatRoomApplicationService chatRoomApplicationService,
            ApplicationEventPublisher eventPublisher,
            java.time.Clock clock
    ) {
        this.transactionRepository = transactionRepository;
        this.itemApplicationService = itemApplicationService;
        this.pointApplicationService = pointApplicationService;
        this.userApplicationService = userApplicationService;
        this.chatRoomApplicationService = chatRoomApplicationService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    

    @Transactional
    public Long create(TransactionCreateCommand cmd) {
        if (cmd.chatRoomId() == null) {
            throw new BusinessException(ErrorCode.TX_CHATROOM_REQUIRED);
        }
        
        userApplicationService.requireVerified(cmd.requesterId());

        
        ItemForTransactionResult info = itemApplicationService.findActiveForTransaction(cmd.itemId());

        
        ChatRoomApplicationService.ChatRoomMeta meta =
                chatRoomApplicationService.findMetaForParticipant(cmd.chatRoomId(), cmd.requesterId());
        if (!meta.itemId().equals(cmd.itemId())) {
            throw new BusinessException(ErrorCode.TX_CHATROOM_ITEM_MISMATCH);
        }

        
        if (!info.sellerId().equals(cmd.requesterId())) {
            throw new BusinessException(ErrorCode.TX_SELLER_ONLY);
        }

        
        if (transactionRepository.existsActiveByChatRoomId(cmd.chatRoomId())) {
            throw new BusinessException(ErrorCode.TX_ALREADY_ACTIVE_IN_ROOM);
        }

        
        Long buyerId = chatRoomApplicationService.findOpponent(cmd.chatRoomId(), cmd.requesterId());

        
        
        com.sseulang.domain.item.domain.TradeType mode = meta.tradeMode();
        Long price = info.priceFor(mode);
        if (price == null) {
            throw new BusinessException(ErrorCode.ITEM_INVALID_STATE);
        }
        Long deposit = mode == com.sseulang.domain.item.domain.TradeType.대여 ? info.deposit() : null;

        Transaction tx = Transaction.create(
                info.itemId(),
                info.sellerId(),
                buyerId,
                mode,
                price,
                deposit,
                cmd.rentalStart(),
                cmd.rentalEnd(),
                cmd.chatRoomId()
        );
        return transactionRepository.save(tx).getId();
    }

    

    // 직거래는 사이트 포인트 거래 없음(외부 결제). reserve 는 Item 잠금 + 상태 마킹만.
    @Transactional
    public void reserve(Long transactionId, Long requesterId) {
        Transaction tx = findOrThrowForUpdate(transactionId);
        if (!tx.isSeller(requesterId)) {
            throw new BusinessException(ErrorCode.TRANSACTION_FORBIDDEN);
        }
        itemApplicationService.markItemAsReserved(tx.getItemId());
        LocalDateTime now = LocalDateTime.now(clock);
        tx.markAsReserved(now, 0L);
        eventPublisher.publishEvent(new TransactionReservedEvent(
                tx.getId(), tx.getBuyerId(), tx.getSellerId(), tx.getPrice()));
    }

    

    @Transactional
    public void markHandover(Long transactionId, Long requesterId) {
        Transaction tx = findOrThrowForUpdate(transactionId);
        if (!tx.isSeller(requesterId)) {
            throw new BusinessException(ErrorCode.TRANSACTION_HANDOVER_NOT_ALLOWED);
        }
        if (tx.getStatus() == TransactionStatus.인계완료) {
            return;  
        }
        tx.markHandover(LocalDateTime.now(clock));
        eventPublisher.publishEvent(new TransactionHandoverConfirmedEvent(tx.getId(), tx.getBuyerId()));
    }

    

    @Transactional
    public void markReceived(Long transactionId, Long requesterId) {
        Transaction tx = findOrThrowForUpdate(transactionId);
        if (!tx.isBuyer(requesterId)) {
            throw new BusinessException(ErrorCode.TRANSACTION_RECEIVE_NOT_ALLOWED);
        }
        if (tx.getStatus() == TransactionStatus.거래완료) {
            return;
        }
        itemApplicationService.markItemAsSold(tx.getItemId());
        tx.markReceived(LocalDateTime.now(clock));
        eventPublisher.publishEvent(new TransactionReceiveConfirmedEvent(
                tx.getId(), tx.getSellerId(), 0L));
    }

    // 라운드 12 — 거래대행 정산 시 paired Transaction 자동 생성.
    // 이미 paired Tx 있으면 (UNIQUE 가드) idempotent — 기존 id 반환.
    @Transactional
    public Long createFromEscrow(
            Long escrowApplicationId,
            Long itemId,
            Long sellerId, Long buyerId,
            long itemPrice,
            Long chatRoomId,
            LocalDateTime settledAt
    ) {
        return transactionRepository.findByEscrowApplicationId(escrowApplicationId)
                .map(Transaction::getId)
                .orElseGet(() -> transactionRepository.save(Transaction.createFromEscrow(
                        escrowApplicationId, itemId, sellerId, buyerId, itemPrice, chatRoomId, settledAt
                )).getId());
    }

    // 라운드 12 — 판매자가 한 번에 거래완료. 직거래는 사이트 포인트 거래 없음.
    @Transactional
    public void completeBySeller(Long transactionId, Long requesterId) {
        Transaction tx = findOrThrowForUpdate(transactionId);
        if (!tx.isSeller(requesterId)) {
            throw new BusinessException(ErrorCode.TRANSACTION_FORBIDDEN);
        }
        if (tx.getStatus() == TransactionStatus.거래완료) {
            return;
        }
        // Item 잠금 / 판매완료 전이
        if (tx.getStatus() == TransactionStatus.채팅중) {
            itemApplicationService.markItemAsReserved(tx.getItemId());
        }
        itemApplicationService.markItemAsSold(tx.getItemId());
        tx.completeBySeller(LocalDateTime.now(clock));
        eventPublisher.publishEvent(new TransactionReceiveConfirmedEvent(
                tx.getId(), tx.getSellerId(), 0L));
    }


    @Transactional
    public void cancel(Long transactionId, Long requesterId, String reason) {
        Transaction tx = findOrThrowForUpdate(transactionId);
        if (!tx.isParticipant(requesterId)) {
            throw new BusinessException(ErrorCode.TRANSACTION_FORBIDDEN);
        }
        boolean wasReserved = tx.getStatus() == TransactionStatus.예약
                || tx.getStatus() == TransactionStatus.인계완료;
        tx.cancel(LocalDateTime.now(clock), reason);
        if (wasReserved) {
            itemApplicationService.restoreItemFromReserved(tx.getItemId());
        }
        eventPublisher.publishEvent(new TransactionCanceledEvent(
                tx.getId(), tx.getBuyerId(), tx.getSellerId(), requesterId));
    }

    public TransactionResult getById(Long id, Long requesterId) {
        Transaction tx = findOrThrow(id);
        if (!tx.isParticipant(requesterId)) {
            throw new BusinessException(ErrorCode.TRANSACTION_FORBIDDEN);
        }
        return TransactionResult.from(tx);
    }

    

    public ReviewableTransactionResult findCompletedForReview(Long transactionId, Long requesterId) {
        Transaction tx = findOrThrow(transactionId);
        if (!tx.isParticipant(requesterId)) {
            throw new BusinessException(ErrorCode.TRANSACTION_FORBIDDEN);
        }
        if (tx.getStatus() != TransactionStatus.거래완료 || tx.getCompletedAt() == null) {
            throw new BusinessException(ErrorCode.TRANSACTION_INVALID_STATE);
        }
        
        if (LocalDateTime.now(clock).isAfter(tx.getCompletedAt().plusDays(7))) {
            throw new BusinessException(ErrorCode.REVIEW_PERIOD_EXPIRED);
        }
        Long revieweeId = tx.isSeller(requesterId) ? tx.getBuyerId() : tx.getSellerId();
        return new ReviewableTransactionResult(tx.getId(), requesterId, revieweeId, tx.getCompletedAt());
    }

    
    public java.util.List<com.sseulang.domain.transaction.domain.TransactionRepository.TradeTypeCount>
            countByTradeTypeBetween(java.time.LocalDateTime from, java.time.LocalDateTime to) {
        return transactionRepository.countByTradeTypeBetween(from, to);
    }

    
    public java.util.List<TransactionStatusCount> countByStatusBetween(
            java.time.LocalDateTime from, java.time.LocalDateTime to) {
        return transactionRepository.countByStatusBetween(from, to);
    }

    

    public TransactionStatsResult adminGetStats() {
        Map<TransactionStatus, Long> byStatus = new EnumMap<>(TransactionStatus.class);
        for (TransactionStatus s : TransactionStatus.values()) {
            byStatus.put(s, 0L);  
        }
        long total = 0;
        for (TransactionStatusCount row : transactionRepository.countGroupByStatus()) {
            byStatus.put(row.status(), row.count());
            total += row.count();
        }
        return new TransactionStatsResult(total, byStatus);
    }

    

    public java.util.List<com.sseulang.domain.transaction.domain.TransactionMonthlyStat> adminMonthlyTrades(
            java.time.YearMonth from, java.time.YearMonth to) {
        java.util.Map<java.time.YearMonth, com.sseulang.domain.transaction.domain.TransactionMonthlyStat> byMonth = new java.util.LinkedHashMap<>();
        for (java.time.YearMonth m = from; !m.isAfter(to); m = m.plusMonths(1)) {
            byMonth.put(m, new com.sseulang.domain.transaction.domain.TransactionMonthlyStat(m, 0, 0));
        }
        for (var row : transactionRepository.countCompletedMonthly(from, to)) {
            byMonth.put(row.month(), row);
        }
        return new java.util.ArrayList<>(byMonth.values());
    }

    
    private static final int KEYWORD_USER_LIMIT = 200;

    

    public Page<TransactionResult> adminSearch(
            java.time.LocalDateTime startDate,
            java.time.LocalDateTime endDate,
            com.sseulang.domain.item.domain.TradeType tradeType,
            TransactionStatus status,
            String keyword,
            Pageable pageable
    ) {
        java.util.List<Long> matchedUserIds = java.util.Collections.emptyList();
        if (keyword != null && !keyword.isBlank() && parseLong(keyword) == null) {
            matchedUserIds = userApplicationService.findUserIdsByKeyword(keyword, KEYWORD_USER_LIMIT);
        }
        return transactionRepository.adminSearch(
                        startDate, endDate, tradeType, status, keyword, matchedUserIds, pageable)
                .map(TransactionResult::from);
    }

    private static Long parseLong(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    

    

    public Page<TransactionResult> findMyTransactions(
            Long userId, TransactionRole role, TransactionStatus status, Pageable pageable) {
        return transactionRepository.findMyTransactions(userId, role, status, pageable)
                .map(TransactionResult::from);
    }

    public Page<PendingReviewableResult> findPendingReviewable(Long userId, Pageable pageable) {
        LocalDateTime since = LocalDateTime.now(clock).minusDays(7);
        return transactionRepository.findPendingReviewable(userId, since, pageable)
                .map(tx -> {
                    Long revieweeId = tx.isSeller(userId) ? tx.getBuyerId() : tx.getSellerId();
                    return new PendingReviewableResult(
                            tx.getId(),
                            tx.getItemId(),
                            revieweeId,
                            tx.getTradeType(),
                            tx.getPrice(),
                            tx.getCompletedAt(),
                            tx.getCompletedAt().plusDays(7)
                    );
                });
    }

    private Transaction findOrThrow(Long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND));
    }

    private Transaction findOrThrowForUpdate(Long id) {
        return transactionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND));
    }
}
