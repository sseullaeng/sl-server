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

    // 라운드 12 — 거래대행 paired Transaction 은 Item 없을 수 있음(EXTERNAL).
    @Column(name = "item_id")
    private Long itemId;

    // 라운드 12 — 거래대행 paired Transaction 의 1:1 역참조. UNIQUE 가드로 중복 paired 방지.
    @Column(name = "escrow_application_id", updatable = false)
    private Long escrowApplicationId;

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

    @Column(name = "return_requested_at")
    private LocalDateTime returnRequestedAt;

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
        // 직거래는 Item 필수. paired-from-escrow 는 createFromEscrow 팩토리 사용.
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
        // 대여는 인계완료 → 반납요청 → 회신확인 강제. markReceived 직접 호출로 우회 차단.
        if (this.tradeType == TradeType.대여) {
            throw new BusinessException(ErrorCode.TRANSACTION_INVALID_STATE);
        }
        if (!status.canReceive()) {
            throw new BusinessException(ErrorCode.TRANSACTION_INVALID_STATE);
        }
        this.status = TransactionStatus.거래완료;
        this.receiveConfirmedAt = now;
        this.completedAt = now;
    }

    // B-6: buyer(빌린 사람) 가 반납 요청. 대여 한정. 인계완료 → 반납요청.
    public void requestReturn(Long buyerId, LocalDateTime now) {
        if (this.tradeType != TradeType.대여) {
            throw new BusinessException(ErrorCode.TRANSACTION_INVALID_STATE);
        }
        if (!isBuyer(buyerId)) {
            throw new BusinessException(ErrorCode.TRANSACTION_FORBIDDEN);
        }
        if (!status.canRequestReturn()) {
            throw new BusinessException(ErrorCode.TRANSACTION_INVALID_STATE);
        }
        this.status = TransactionStatus.반납요청;
        this.returnRequestedAt = now;
    }

    // B-6: seller(빌려준 사람) 가 회신 = 거래완료. 대여 한정. 반납요청 → 거래완료.
    public void confirmReturn(Long sellerId, LocalDateTime now) {
        if (this.tradeType != TradeType.대여) {
            throw new BusinessException(ErrorCode.TRANSACTION_INVALID_STATE);
        }
        if (!isSeller(sellerId)) {
            throw new BusinessException(ErrorCode.TRANSACTION_FORBIDDEN);
        }
        if (!status.canConfirmReturn()) {
            throw new BusinessException(ErrorCode.TRANSACTION_INVALID_STATE);
        }
        this.status = TransactionStatus.거래완료;
        this.completedAt = now;
    }

    // B-6: 7일 자동 거래완료 — 스케줄러 호출용. 권한 가드 없음(시스템).
    public void autoCompleteFromReturnRequest(LocalDateTime now) {
        if (this.tradeType != TradeType.대여 || !status.canConfirmReturn()) {
            throw new BusinessException(ErrorCode.TRANSACTION_INVALID_STATE);
        }
        this.status = TransactionStatus.거래완료;
        this.completedAt = now;
    }

    // 라운드 12 — 직거래 단순 완료. 사이트 포인트 거래 없음(외부 결제).
    // 채팅중/예약/인계완료 어떤 상태에서든 거래완료로 전이. 판매자 호출 가정.
    // 대여는 반납요청 → 회신확인(seller) 강제 — completeBySeller 우회 차단.
    public void completeBySeller(LocalDateTime now) {
        if (this.tradeType == TradeType.대여) {
            throw new BusinessException(ErrorCode.TRANSACTION_INVALID_STATE);
        }
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

    // 라운드 12 — 거래대행 정산 시점에 paired Transaction 자동 생성.
    // tradeType 매핑: itemPrice==0(나눔) → 나눔, 그 외 → 판매. (대여는 거래대행 흐름에 안 들어옴)
    // Item 은 INTERNAL escrow 면 linkedItem, EXTERNAL 이면 null.
    public static Transaction createFromEscrow(
            Long escrowApplicationId,
            Long itemId,
            Long sellerId, Long buyerId,
            long itemPrice,
            Long chatRoomId,
            LocalDateTime settledAt
    ) {
        if (escrowApplicationId == null || escrowApplicationId <= 0) {
            throw new IllegalArgumentException("escrowApplicationId 는 양수여야 합니다");
        }
        if (sellerId == null || sellerId <= 0 || buyerId == null || buyerId <= 0) {
            throw new IllegalArgumentException("seller/buyerId 양수 필수");
        }
        if (sellerId.equals(buyerId)) {
            throw new IllegalArgumentException("seller 와 buyer 는 같을 수 없습니다");
        }
        if (itemPrice < 0) {
            throw new IllegalArgumentException("itemPrice 는 0 이상이어야 합니다");
        }
        if (settledAt == null) {
            throw new IllegalArgumentException("settledAt 필수");
        }
        Transaction t = new Transaction();
        t.escrowApplicationId = escrowApplicationId;
        t.itemId = itemId;   // nullable
        t.sellerId = sellerId;
        t.buyerId = buyerId;
        t.tradeType = itemPrice == 0 ? TradeType.나눔 : TradeType.판매;
        t.price = itemPrice;
        t.deposit = null;
        t.rentalStart = null;
        t.rentalEnd = null;
        t.chatRoomId = chatRoomId;
        t.status = TransactionStatus.거래완료;
        t.reservedAt = settledAt;
        t.handoverConfirmedAt = settledAt;
        t.receiveConfirmedAt = settledAt;
        t.completedAt = settledAt;
        return t;
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
