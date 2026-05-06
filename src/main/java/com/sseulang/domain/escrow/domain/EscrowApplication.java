package com.sseulang.domain.escrow.domain;

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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Escrow Application Aggregate Root. V15 {@code escrow_applications} 매핑.
 *
 * <p>폼 제출 후 단일 row. 결제/정산/취소 라이프사이클 모두 본 Aggregate 이 책임.
 * snapshot (applied_*) — settings 변경 무관 lock (결정 #9, #12).</p>
 *
 * <p>상태머신: 결제대기 → 결제완료 → 진행중 → 완료 / 취소.
 * 양쪽 share 결제 (G2) — initiator/receiver 각자 결제 시점 기록.</p>
 */
@Entity
@Table(name = "escrow_applications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EscrowApplication extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "link_id", nullable = false, updatable = false)
    private Long linkId;

    @Column(name = "initiator_id", nullable = false, updatable = false)
    private Long initiatorId;

    @Column(name = "receiver_id", nullable = false, updatable = false)
    private Long receiverId;

    @Column(name = "buyer_id", nullable = false, updatable = false)
    private Long buyerId;

    @Column(name = "seller_id", nullable = false, updatable = false)
    private Long sellerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trade_mode", nullable = false, length = 20, updatable = false)
    private TradeMode tradeMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_payer", nullable = false, length = 10, updatable = false)
    private FeePayer feePayer;

    @Column(name = "item_price", nullable = false, updatable = false)
    private long itemPrice;

    @Column(name = "item_description", nullable = false, length = 500, updatable = false)
    private String itemDescription;

    @Column(name = "pickup_address", nullable = false, length = 255, updatable = false)
    private String pickupAddress;

    @Column(name = "pickup_lat", nullable = false, precision = 10, scale = 7, updatable = false)
    private BigDecimal pickupLat;

    @Column(name = "pickup_lng", nullable = false, precision = 10, scale = 7, updatable = false)
    private BigDecimal pickupLng;

    @Column(name = "delivery_address", nullable = false, length = 255, updatable = false)
    private String deliveryAddress;

    @Column(name = "delivery_lat", nullable = false, precision = 10, scale = 7, updatable = false)
    private BigDecimal deliveryLat;

    @Column(name = "delivery_lng", nullable = false, precision = 10, scale = 7, updatable = false)
    private BigDecimal deliveryLng;

    @Enumerated(EnumType.STRING)
    @Column(name = "weight", nullable = false, length = 10, updatable = false)
    private Weight weight;

    @Enumerated(EnumType.STRING)
    @Column(name = "volume", nullable = false, length = 5, updatable = false)
    private Volume volume;

    @Enumerated(EnumType.STRING)
    @Column(name = "fragility", nullable = false, length = 5, updatable = false)
    private Fragility fragility;

    @Column(name = "delivery_notes", length = 500, updatable = false)
    private String deliveryNotes;

    // ===== snapshot — 운영 settings 변경 무관 lock =====
    @Column(name = "applied_distance_km", nullable = false, precision = 8, scale = 2, updatable = false)
    private BigDecimal appliedDistanceKm;

    @Column(name = "applied_delivery_fee", nullable = false, updatable = false)
    private long appliedDeliveryFee;

    @Column(name = "applied_commission_fee", nullable = false, updatable = false)
    private long appliedCommissionFee;

    @Column(name = "applied_total_fee", nullable = false, updatable = false)
    private long appliedTotalFee;

    @Column(name = "applied_commission_rate", nullable = false, precision = 5, scale = 4, updatable = false)
    private BigDecimal appliedCommissionRate;

    // ===== 결제 추적 =====
    @Column(name = "initiator_share", nullable = false, updatable = false)
    private long initiatorShare;

    @Column(name = "receiver_share", nullable = false, updatable = false)
    private long receiverShare;

    @Column(name = "initiator_paid_at")
    private LocalDateTime initiatorPaidAt;

    @Column(name = "receiver_paid_at")
    private LocalDateTime receiverPaidAt;

    @Column(name = "payment_due_at")
    private LocalDateTime paymentDueAt;

    // ===== 상태머신 =====
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EscrowApplicationStatus status;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "receipt_confirmed_at")
    private LocalDateTime receiptConfirmedAt;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Column(name = "image_urls", columnDefinition = "TEXT", updatable = false)
    private String imageUrls;

    /**
     * 폼 제출 후 application 생성. snapshot 컬럼 모두 채움.
     * shares 는 feePayer 별 ApplicationService 가 산정 후 주입.
     */
    public static EscrowApplication create(
            Long linkId,
            Long initiatorId, Long receiverId, InitiatorRole initiatorRole,
            TradeMode tradeMode, FeePayer feePayer,
            long itemPrice, String itemDescription,
            String pickupAddress, BigDecimal pickupLat, BigDecimal pickupLng,
            String deliveryAddress, BigDecimal deliveryLat, BigDecimal deliveryLng,
            Weight weight, Volume volume, Fragility fragility, String deliveryNotes,
            FeeBreakdown snapshot,
            long initiatorShare, long receiverShare,
            String imageUrls
    ) {
        if (linkId == null || initiatorId == null || receiverId == null) {
            throw new IllegalArgumentException("ids required");
        }
        if (initiatorId.equals(receiverId)) {
            throw new BusinessException(ErrorCode.ESCROW_SELF_NOT_ALLOWED);
        }
        // role 매핑 — buyer/seller 식별
        Long buyerId = initiatorRole == InitiatorRole.buyer ? initiatorId : receiverId;
        Long sellerId = initiatorRole == InitiatorRole.buyer ? receiverId : initiatorId;

        EscrowApplication a = new EscrowApplication();
        a.linkId = linkId;
        a.initiatorId = initiatorId;
        a.receiverId = receiverId;
        a.buyerId = buyerId;
        a.sellerId = sellerId;
        a.tradeMode = tradeMode;
        a.feePayer = feePayer;
        a.itemPrice = itemPrice;
        a.itemDescription = itemDescription;
        a.pickupAddress = pickupAddress;
        a.pickupLat = pickupLat;
        a.pickupLng = pickupLng;
        a.deliveryAddress = deliveryAddress;
        a.deliveryLat = deliveryLat;
        a.deliveryLng = deliveryLng;
        a.weight = weight;
        a.volume = volume;
        a.fragility = fragility;
        a.deliveryNotes = deliveryNotes;
        a.appliedDistanceKm = snapshot.distanceKm();
        a.appliedDeliveryFee = snapshot.deliveryFee();
        a.appliedCommissionFee = snapshot.commissionFee();
        a.appliedTotalFee = snapshot.totalFee();
        a.appliedCommissionRate = snapshot.commissionRate();
        a.initiatorShare = initiatorShare;
        a.receiverShare = receiverShare;
        a.status = EscrowApplicationStatus.결제대기;
        a.imageUrls = imageUrls;
        return a;
    }

    /** 수신자 결제 완료 — 결정 #5 H2 (수신자 먼저). 모든 share 충족 시 결제완료 진입. */
    public void markReceiverPaid() {
        if (this.status != EscrowApplicationStatus.결제대기) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        if (this.receiverPaidAt != null) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        this.receiverPaidAt = LocalDateTime.now();
        // 첫 결제 시점 — payment_due_at = +24h
        if (this.paymentDueAt == null) {
            this.paymentDueAt = this.receiverPaidAt.plusHours(24);
        }
        tryAdvanceToConfirmed();
    }

    public void markInitiatorPaid() {
        if (this.status != EscrowApplicationStatus.결제대기) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        if (this.initiatorPaidAt != null) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        this.initiatorPaidAt = LocalDateTime.now();
        if (this.paymentDueAt == null) {
            this.paymentDueAt = this.initiatorPaidAt.plusHours(24);
        }
        tryAdvanceToConfirmed();
    }

    /** 양쪽 share 모두 충족됐는지 확인 후 결제완료 전진. share=0 인 쪽은 결제 면제. */
    private void tryAdvanceToConfirmed() {
        boolean initiatorOk = this.initiatorShare == 0 || this.initiatorPaidAt != null;
        boolean receiverOk = this.receiverShare == 0 || this.receiverPaidAt != null;
        if (initiatorOk && receiverOk) {
            this.status = EscrowApplicationStatus.결제완료;
        }
    }

    /** 라이더 자동 매칭됐을 때 — 결정 #8 X1. */
    public void markInProgress() {
        if (this.status != EscrowApplicationStatus.결제완료) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        this.status = EscrowApplicationStatus.진행중;
    }

    /** Mode B buyer 수령 확인 — 결정 #4. 정산 흐름 트리거. */
    public void confirmReceipt(Long requesterId) {
        if (this.tradeMode != TradeMode.INTERNAL) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        if (this.status != EscrowApplicationStatus.진행중) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        if (!this.buyerId.equals(requesterId)) {
            throw new BusinessException(ErrorCode.ESCROW_FORBIDDEN);
        }
        this.receiptConfirmedAt = LocalDateTime.now();
    }

    /** 정산 완료 표시 — ApplicationService 가 포인트 이동 후 호출. */
    public void markSettled() {
        if (this.status != EscrowApplicationStatus.진행중) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        this.status = EscrowApplicationStatus.완료;
        this.settledAt = LocalDateTime.now();
    }

    /** 취소 — 시점별 환불은 ApplicationService 가 처리. 본 Aggregate 는 상태만 표시. */
    public void cancel(Long cancelledBy, String reason) {
        if (this.status.isTerminal()) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        this.status = EscrowApplicationStatus.취소;
        this.cancelledBy = cancelledBy;
        this.cancelReason = reason;
    }

    public boolean isPaymentTimedOut() {
        return this.paymentDueAt != null && LocalDateTime.now().isAfter(this.paymentDueAt)
                && this.status == EscrowApplicationStatus.결제대기;
    }

    /** 사용자가 양쪽 당사자인지 확인. */
    public boolean isParticipant(Long userId) {
        return this.initiatorId.equals(userId) || this.receiverId.equals(userId);
    }
}
