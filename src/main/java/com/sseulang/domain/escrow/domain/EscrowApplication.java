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

    /**
     * 외부 link 흐름의 link.id. 내부 chatRoom 흐름은 NULL (V20 라운드 12 PR-B-2).
     */
    @Column(name = "link_id", updatable = false)
    private Long linkId;

    /**
     * 진입 경로 — INTERNAL (채팅방 내 신청) | EXTERNAL (link 토큰 흐름).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10, updatable = false)
    private EntryType entryType;

    /**
     * 내부 흐름의 채팅방 ID (V19 컬럼). 외부 link 흐름은 NULL.
     */
    @Column(name = "chat_room_id", updatable = false)
    private Long chatRoomId;

    /**
     * 판매자 영역 (출발지/물품) 입력 완료 — PR-B-4 라운드 12. 외부 흐름은 항상 TRUE.
     */
    @Column(name = "seller_info_filled", nullable = false)
    private boolean sellerInfoFilled;

    /**
     * 구매자 영역 (수령지/연락처) 입력 완료. 외부 흐름은 항상 TRUE.
     * 양쪽 모두 TRUE 가 되면 fee 산정 + status 가 정보입력대기 → 결제대기 로 전환.
     */
    @Column(name = "buyer_info_filled", nullable = false)
    private boolean buyerInfoFilled;

    /**
     * 수령자 연락처 (구매자 영역) — 내부 흐름의 buyer-info PATCH 시 입력.
     */
    @Column(name = "receiver_phone", length = 20)
    private String receiverPhone;

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

    /** 수령지 — 내부 draft 단계엔 NULL, buyer-info PATCH 시 입력. */
    @Column(name = "delivery_address", length = 255)
    private String deliveryAddress;

    @Column(name = "delivery_lat", precision = 10, scale = 7)
    private BigDecimal deliveryLat;

    @Column(name = "delivery_lng", precision = 10, scale = 7)
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

    // ===== snapshot — 양쪽 입력 완료 후 산정. 정보입력대기 단계엔 NULL. =====
    @Column(name = "applied_distance_km", precision = 8, scale = 2)
    private BigDecimal appliedDistanceKm;

    @Column(name = "applied_delivery_fee")
    private Long appliedDeliveryFee;

    @Column(name = "applied_commission_fee")
    private Long appliedCommissionFee;

    @Column(name = "applied_total_fee")
    private Long appliedTotalFee;

    @Column(name = "applied_commission_rate", precision = 5, scale = 4)
    private BigDecimal appliedCommissionRate;

    // ===== 결제 추적 =====
    /** 신청자(initiator) 결제 분담분. 양쪽 입력 완료 후 산정. */
    @Column(name = "initiator_share")
    private Long initiatorShare;

    @Column(name = "receiver_share")
    private Long receiverShare;

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
        a.entryType = EntryType.EXTERNAL;
        // 외부 link 흐름은 receiver 가 form 제출 시 양쪽 정보 모두 입력 → 양쪽 filled.
        a.sellerInfoFilled = true;
        a.buyerInfoFilled = true;
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

    /**
     * 내부 chatRoom 흐름 (PR-B-2 라운드 12). 채팅방 안에서 판매자가 한 번에 양쪽 정보 입력.
     *
     * <p>특징 (외부 link 흐름과 차이):
     * <ul>
     *   <li>{@code linkId = null} — link 미사용</li>
     *   <li>{@code entryType = INTERNAL}</li>
     *   <li>initiator = 신청자 (판매자), receiver = 채팅방 상대방 (구매자) — 정책상 sellerOnly 라 initiatorRole=seller 고정</li>
     * </ul>
     */
    public static EscrowApplication createInternal(
            Long chatRoomId,
            Long initiatorId, Long receiverId,
            TradeMode tradeMode, FeePayer feePayer,
            long itemPrice, String itemDescription,
            String pickupAddress, BigDecimal pickupLat, BigDecimal pickupLng,
            String deliveryAddress, BigDecimal deliveryLat, BigDecimal deliveryLng,
            Weight weight, Volume volume, Fragility fragility, String deliveryNotes,
            FeeBreakdown snapshot,
            long initiatorShare, long receiverShare,
            String imageUrls
    ) {
        if (initiatorId == null || receiverId == null) {
            throw new IllegalArgumentException("ids required");
        }
        if (initiatorId.equals(receiverId)) {
            throw new BusinessException(ErrorCode.ESCROW_SELF_NOT_ALLOWED);
        }
        // 내부 흐름은 판매자만 시작 — initiator = seller, receiver = buyer.
        Long sellerId = initiatorId;
        Long buyerId = receiverId;

        if (chatRoomId == null) {
            throw new IllegalArgumentException("chatRoomId required for INTERNAL");
        }
        EscrowApplication a = new EscrowApplication();
        a.linkId = null;
        a.entryType = EntryType.INTERNAL;
        a.chatRoomId = chatRoomId;
        // PR-B-3 단순 흐름 — 현재 createInternal 은 양쪽 정보 한 번에 입력. PR-B-4 의 draft 흐름은
        // 별도 createInternalDraft + patchSellerInfo / patchBuyerInfo 로 분리 (후속 commit).
        a.sellerInfoFilled = true;
        a.buyerInfoFilled = true;
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

    /**
     * 내부 draft 흐름 (PR-B-4 라운드 12) — 판매자가 본인 영역만 입력하여 application 생성.
     *
     * <p>특징:
     * <ul>
     *   <li>{@code status = 정보입력대기}, {@code sellerInfoFilled = true}, {@code buyerInfoFilled = false}</li>
     *   <li>구매자 영역 (delivery_*, receiver_phone) 은 NULL — 구매자가 buyer-info PATCH 시 입력</li>
     *   <li>fee snapshot / share 는 NULL — 양쪽 입력 완료 후 transitionToReadyForPayment 시점에 산정</li>
     *   <li>initiator = seller, receiver = buyer (내부 흐름은 sellerOnly 정책)</li>
     * </ul>
     */
    public static EscrowApplication createInternalDraft(
            Long chatRoomId,
            Long initiatorId, Long receiverId,
            TradeMode tradeMode, FeePayer feePayer,
            long itemPrice, String itemDescription,
            String pickupAddress, BigDecimal pickupLat, BigDecimal pickupLng,
            Weight weight, Volume volume, Fragility fragility, String deliveryNotes,
            String imageUrls
    ) {
        if (chatRoomId == null || initiatorId == null || receiverId == null) {
            throw new IllegalArgumentException("ids required");
        }
        if (initiatorId.equals(receiverId)) {
            throw new BusinessException(ErrorCode.ESCROW_SELF_NOT_ALLOWED);
        }
        EscrowApplication a = new EscrowApplication();
        a.linkId = null;
        a.entryType = EntryType.INTERNAL;
        a.chatRoomId = chatRoomId;
        a.sellerInfoFilled = true;
        a.buyerInfoFilled = false;
        a.initiatorId = initiatorId;
        a.receiverId = receiverId;
        a.sellerId = initiatorId;   // 판매자만 시작
        a.buyerId = receiverId;
        a.tradeMode = tradeMode;
        a.feePayer = feePayer;
        a.itemPrice = itemPrice;
        a.itemDescription = itemDescription;
        a.pickupAddress = pickupAddress;
        a.pickupLat = pickupLat;
        a.pickupLng = pickupLng;
        a.weight = weight;
        a.volume = volume;
        a.fragility = fragility;
        a.deliveryNotes = deliveryNotes;
        a.status = EscrowApplicationStatus.정보입력대기;
        a.imageUrls = imageUrls;
        // delivery_*, receiver_phone, applied_*, share 는 NULL — buyer-info PATCH 시점에 채워짐.
        return a;
    }

    /**
     * 판매자 영역 수정 (PR-B-4) — 정보입력대기 상태에서만. updatable 컬럼 대상.
     * delivery 좌표는 영향 없음 (구매자 영역).
     */
    public void patchSellerInfo(
            String pickupAddress, BigDecimal pickupLat, BigDecimal pickupLng,
            Weight weight, Volume volume, Fragility fragility,
            long itemPrice, String itemDescription, String deliveryNotes
    ) {
        if (this.status != EscrowApplicationStatus.정보입력대기) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        this.pickupAddress = pickupAddress;
        this.pickupLat = pickupLat;
        this.pickupLng = pickupLng;
        this.weight = weight;
        this.volume = volume;
        this.fragility = fragility;
        this.itemPrice = itemPrice;
        this.itemDescription = itemDescription;
        this.deliveryNotes = deliveryNotes;
        this.sellerInfoFilled = true;
    }

    /**
     * 구매자 영역 입력 (PR-B-4) — 정보입력대기 상태에서만.
     * buyerInfoFilled=true 로 set. 호출자(ApplicationService)가 양쪽 filled 시 transitionToReadyForPayment 호출.
     */
    public void patchBuyerInfo(
            String deliveryAddress, BigDecimal deliveryLat, BigDecimal deliveryLng,
            String receiverPhone
    ) {
        if (this.status != EscrowApplicationStatus.정보입력대기) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        this.deliveryAddress = deliveryAddress;
        this.deliveryLat = deliveryLat;
        this.deliveryLng = deliveryLng;
        this.receiverPhone = receiverPhone;
        this.buyerInfoFilled = true;
    }

    /**
     * 양쪽 입력 완료 — fee 산정 결과 + share 를 set + 결제대기 진입.
     * ApplicationService 가 EscrowFeeCalculator 로 산정한 snapshot 을 주입.
     */
    public void transitionToReadyForPayment(FeeBreakdown snapshot, long initiatorShare, long receiverShare) {
        if (this.status != EscrowApplicationStatus.정보입력대기) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        if (!this.sellerInfoFilled || !this.buyerInfoFilled) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        this.appliedDistanceKm = snapshot.distanceKm();
        this.appliedDeliveryFee = snapshot.deliveryFee();
        this.appliedCommissionFee = snapshot.commissionFee();
        this.appliedTotalFee = snapshot.totalFee();
        this.appliedCommissionRate = snapshot.commissionRate();
        this.initiatorShare = initiatorShare;
        this.receiverShare = receiverShare;
        this.status = EscrowApplicationStatus.결제대기;
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
