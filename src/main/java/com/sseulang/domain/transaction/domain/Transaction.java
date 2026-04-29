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

/**
 * Transaction Aggregate Root. V1 스키마 {@code transactions} 매핑.
 *
 * <p>가이드 §5.1 거래 상태 머신: {@code 채팅중 → 예약 → 거래완료} (또는 {@code 취소}).
 * 동시 예약 차단은 본 Aggregate 가 아니라 ApplicationService 가 Item 비관적 락 + Item.markAsReserved
 * conditional 전이로 처리. 본 Aggregate 자체는 단일 거래의 상태 머신만 책임.</p>
 *
 * <p>Setter 없음. 상태 변경은 명시 비즈니스 메서드만.</p>
 */
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

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    public static Transaction create(
            Long itemId,
            Long sellerId,
            Long buyerId,
            TradeType tradeType,
            long price,
            Long deposit,
            LocalDateTime rentalStart,
            LocalDateTime rentalEnd
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
        t.status = TransactionStatus.채팅중;
        return t;
    }

    public void markAsReserved(LocalDateTime now) {
        if (!status.canReserve()) {
            throw new BusinessException(ErrorCode.TRANSACTION_INVALID_STATE);
        }
        this.status = TransactionStatus.예약;
        this.reservedAt = now;
    }

    public void markAsCompleted(LocalDateTime now) {
        if (!status.canComplete()) {
            throw new BusinessException(ErrorCode.TRANSACTION_INVALID_STATE);
        }
        this.status = TransactionStatus.거래완료;
        this.completedAt = now;
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
