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

    /**
     * 라운드 12 (#3.2) — 거래 시작 채팅방 가드. V19 추가. 거래는 채팅방 안에서만 시작 가능,
     * 한 채팅방 = 1 active transaction 정책. 이전 라운드 row 는 NULL (legacy).
     */
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

    /**
     * 라운드 11 — seller 인계확인 시각 (V16). 예약 → 인계완료 전이 시점.
     * 본 필드 set 만으로는 의미 없음 — markHandover 가 status 함께 전이.
     */
    @Column(name = "handover_confirmed_at")
    private LocalDateTime handoverConfirmedAt;

    /**
     * 라운드 11 — buyer 인수확인 시각 (V16). 인계완료 → 거래완료 전이 시점 + 정산 트리거.
     * 본 필드 set 만으로는 의미 없음 — markReceived 가 status 함께 전이 + completedAt 동기 set.
     */
    @Column(name = "receive_confirmed_at")
    private LocalDateTime receiveConfirmedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    /**
     * 라운드 11 — 예약 시 buyer point_balance 에서 본 컬럼으로 hold 한 금액 (V16). 일반적으로 price 와
     * 동일하지만, 환불/감사 추적용으로 명시 컬럼화. 채팅중/예약 외 단계에서 cancel 시 환불 금액 결정 키.
     * 나눔 거래 (price=0) 또는 옛 정책 거래는 0.
     */
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

    /**
     * 나눔 거래용 (price=0, hold 0) — 가이드 §4.11. 라운드 11 일반 판매/대여는
     * {@link #markAsReserved(LocalDateTime, long)} 으로 hold 금액 명시.
     */
    public void markAsReserved(LocalDateTime now) {
        markAsReserved(now, 0L);
    }

    /**
     * 라운드 11 예약 — status=예약 + reservedAt + escrowHoldAmount 동기 set. ApplicationService 가
     * 같은 트랜잭션 안에서 PointApplicationService.escrowHold 호출 (buyer balance↓, hold↑).
     *
     * @param holdAmount 0 (나눔/옛) 또는 양수 (price 와 동일). 음수는 IllegalArgumentException.
     */
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

    /**
     * 라운드 11 — seller 인계확인. 예약 → 인계완료 전이. ApplicationService 가 권한 가드 (seller 만)
     * 후 호출. 잘못된 status 에서 호출 시 TRANSACTION_INVALID_STATE.
     */
    public void markHandover(LocalDateTime now) {
        if (!status.canHandover()) {
            throw new BusinessException(ErrorCode.TRANSACTION_INVALID_STATE);
        }
        this.status = TransactionStatus.인계완료;
        this.handoverConfirmedAt = now;
    }

    /**
     * 라운드 11 — buyer 인수확인. 인계완료 → 거래완료 자동 전이 + completedAt 동기 set.
     * ApplicationService 가 같은 트랜잭션 안에서 PointApplicationService.escrowRelease (정산) 호출.
     * receiveConfirmedAt 과 completedAt 은 같은 시각 (자동 전이 의미).
     */
    public void markReceived(LocalDateTime now) {
        if (!status.canReceive()) {
            throw new BusinessException(ErrorCode.TRANSACTION_INVALID_STATE);
        }
        this.status = TransactionStatus.거래완료;
        this.receiveConfirmedAt = now;
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
