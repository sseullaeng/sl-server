package com.sseulang.domain.transaction.domain;

import com.sseulang.domain.item.domain.TradeType;
import com.sseulang.global.common.BaseEntity;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    

    @Column(name = "chat_room_id")
    private Long chatRoomId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trade_type", nullable = false)
    private TradeType tradeType;

    @Column(name = "price", nullable = false)
    private long price;

    @Column(name = "deposit")
    private Long deposit;

    @Column(name = "rental_start")
    private LocalDateTime rentalStart;

    @Column(name = "rental_end")
    private LocalDateTime rentalEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionStatus status;

    @Column(name = "reserved_at")
    private LocalDateTime reservedAt;

    

    @Column(name = "handover_confirmed_at")
    private LocalDateTime handoverConfirmedAt;

    

    @Column(name = "receive_confirmed_at")
    private LocalDateTime receiveConfirmedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    

    @Column(name = "escrow_hold_amount", nullable = false)
    private long escrowHoldAmount;

    public static Transaction create(
            Long itemId,
            Long sellerId,
            Long buyerId,
            TradeType tradeType,
            long price,
            Long deposit,
            LocalDateTime rentalStart,
            LocalDateTime rentalEnd,
            Long chatRoomId
    ) {
        if (itemId == null || itemId <= 0) {
            throw new IllegalArgumentException("itemId 는 양수여야 합니다");
        }
        if (sellerId == null || sellerId <= 0) {
            throw new IllegalArgumentException("sellerId 는 양수여야 합니다");
        }
        if (buyerId == null || buyerId <= 0) {
            throw new IllegalArgumentException("buyerId 는 양수여야 합니다");
        }
        if (sellerId.equals(buyerId)) {
            throw new IllegalArgumentException("seller 와 buyer 는 같을 수 없습니다");
        }
        if (tradeType == null) {
            throw new IllegalArgumentException("tradeType 은 필수입니다");
        }
        if (price < 0) {
            throw new IllegalArgumentException("price 는 0 이상이어야 합니다");
        }
        if (chatRoomId != null && chatRoomId <= 0) {
            throw new IllegalArgumentException("chatRoomId 는 양수여야 합니다");
        }
        validateRentalFields(tradeType, deposit, rentalStart, rentalEnd);

        Transaction t = new Transaction();
        t.itemId = itemId;
        t.sellerId = sellerId;
        t.buyerId = buyerId;
        t.tradeType = tradeType;
        t.price = price;
        t.deposit = deposit;
        t.rentalStart = rentalStart;
        t.rentalEnd = rentalEnd;
        t.chatRoomId = chatRoomId;
        t.status = TransactionStatus.채팅중;
        return t;
    }

    

    public void markAsReserved(LocalDateTime now) {
        markAsReserved(now, 0L);
    }

    

    public void markAsReserved(LocalDateTime now, long holdAmount) {
        if (!status.canReserve()) {
            throw new BusinessException(ErrorCode.TRANSACTION_INVALID_STATE);
        }
        if (holdAmount < 0) {
            throw new IllegalArgumentException("holdAmount 는 0 이상이어야 합니다");
        }
        this.status = TransactionStatus.예약;
        this.reservedAt = now;
        this.escrowHoldAmount = holdAmount;
    }

    

    public void markHandover(LocalDateTime now) {
        if (!status.canHandover()) {
            throw new BusinessException(ErrorCode.TRANSACTION_INVALID_STATE);
        }
        this.status = TransactionStatus.인계완료;
        this.handoverConfirmedAt = now;
    }

    

    public void markReceived(LocalDateTime now) {
        if (!status.canReceive()) {
            throw new BusinessException(ErrorCode.TRANSACTION_INVALID_STATE);
        }
        this.status = TransactionStatus.거래완료;
        this.receiveConfirmedAt = now;
        this.completedAt = now;
    }

    // 라운드 12 — 직거래 단순 완료. 사이트 포인트 거래 없음(외부 결제).
    // 채팅중/예약/인계완료 어떤 상태에서든 거래완료로 전이. 판매자 호출 가정.
    public void completeBySeller(LocalDateTime now) {
        if (status == TransactionStatus.거래완료 || status == TransactionStatus.취소) {
            throw new BusinessException(ErrorCode.TRANSACTION_INVALID_STATE);
        }
        if (this.reservedAt == null) {
            this.reservedAt = now;
        }
        if (this.handoverConfirmedAt == null) {
            this.handoverConfirmedAt = now;
        }
        this.receiveConfirmedAt = now;
        this.completedAt = now;
        this.status = TransactionStatus.거래완료;
    }

    public void cancel(LocalDateTime now, String reason) {
        if (!status.canCancel()) {
            throw new BusinessException(ErrorCode.TRANSACTION_INVALID_STATE);
        }
        this.status = TransactionStatus.취소;
        this.canceledAt = now;
        this.cancelReason = reason;
    }

    public boolean isSeller(Long userId) {
        return userId != null && userId.equals(this.sellerId);
    }

    public boolean isBuyer(Long userId) {
        return userId != null && userId.equals(this.buyerId);
    }

    public boolean isParticipant(Long userId) {
        return isSeller(userId) || isBuyer(userId);
    }

    private static void validateRentalFields(
            TradeType tradeType, Long deposit, LocalDateTime rentalStart, LocalDateTime rentalEnd
    ) {
        if (tradeType.requiresDeposit()) {
            if (deposit == null || deposit < 0) {
                throw new IllegalArgumentException("대여 거래는 0 이상의 deposit 이 필수입니다");
            }
            if (rentalStart == null || rentalEnd == null) {
                throw new IllegalArgumentException("대여 거래는 rentalStart/rentalEnd 가 필수입니다");
            }
            if (!rentalEnd.isAfter(rentalStart)) {
                throw new IllegalArgumentException("rentalEnd 는 rentalStart 보다 이후여야 합니다");
            }
        } else {
            if (deposit != null) {
                throw new IllegalArgumentException("대여 외 거래에는 deposit 을 지정할 수 없습니다");
            }
            if (rentalStart != null || rentalEnd != null) {
                throw new IllegalArgumentException("대여 외 거래에는 rental 일정을 지정할 수 없습니다");
            }
        }
    }
}
