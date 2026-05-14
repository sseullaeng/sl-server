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

@Entity
@Table(name = "escrow_applications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EscrowApplication extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    

    @Column(name = "link_id", updatable = false)
    private Long linkId;

    

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10, updatable = false)
    private EntryType entryType;

    

    @Column(name = "chat_room_id", updatable = false)
    private Long chatRoomId;

    

    @Column(name = "seller_info_filled", nullable = false)
    private boolean sellerInfoFilled;

    

    @Column(name = "buyer_info_filled", nullable = false)
    private boolean buyerInfoFilled;

    

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

    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EscrowApplicationStatus status;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    // 라운드 14 PR7 — 사용중 단계 양 당사자 합의 취소. 한쪽이 요청하면 채워지고, 다른쪽 confirm 시 취소 확정.
    @Column(name = "cancel_requested_by")
    private Long cancelRequestedBy;

    @Column(name = "cancel_requested_at")
    private LocalDateTime cancelRequestedAt;

    @Column(name = "receipt_confirmed_at")
    private LocalDateTime receiptConfirmedAt;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Column(name = "image_urls", columnDefinition = "TEXT", updatable = false)
    private String imageUrls;

    // 라운드 12 — seller 가 [물품 인계] 확인한 시점. 상태 머신 영향 X, UX 용 audit.
    @Column(name = "handover_confirmed_by_seller_at")
    private LocalDateTime handoverConfirmedBySellerAt;

    // 라운드 14 — 대여 거래대행 여부. confirmReceipt 후 사용중/반납중 lifecycle 진입.
    @Column(name = "rental_mode", nullable = false)
    private boolean rentalMode = false;

    // 라운드 14 — 대여 한정. confirmReceipt 시점 (사용중 진입). buyer 가 받은 순간.
    @Column(name = "using_started_at")
    private LocalDateTime usingStartedAt;

    // 라운드 14 — 대여 한정. buyer [반납요청] 시점. return delivery 모집 시작.
    @Column(name = "return_requested_at")
    private LocalDateTime returnRequestedAt;

    // PR6 — 대여 한정. 자동 [반납요청] 스케줄러 기준 종료 예정 시각.
    @Column(name = "rental_end_at")
    private LocalDateTime rentalEndAt;

    // V43 — 대여 한정. 시작 예정 시각. (rentalEndAt - rentalStartAt) 으로 duration 계산하여
    // 백엔드가 itemPrice 자동 산정 (item.rentalPrice × duration).
    @Column(name = "rental_start_at")
    private LocalDateTime rentalStartAt;

    // 라운드 14 — INTERNAL escrow 의 source Item id. paired Tx tradeType/보증금 lookup. EXTERNAL 은 NULL.
    @Column(name = "item_id")
    private Long itemId;

    // 라운드 14 PR8 — 대여 보증금 snapshot. 결제 시 buyer point_hold 로, confirmReturn 시 환불.
    @Column(name = "deposit_amount")
    private Long depositAmount;

    @Column(name = "deposit_original_percent")
    private Integer depositOriginalPercent;

    // INTERNAL escrow 생성 시 호출. EXTERNAL 은 호출 안 함.
    public void linkItem(Long itemId) {
        if (itemId == null || itemId <= 0) {
            throw new IllegalArgumentException("itemId 는 양수여야 합니다");
        }
        this.itemId = itemId;
    }

    // PR8 — 대여 보증금 snapshot. createInternal 시 Item.computeDepositAmount 결과 저장.
    public void setRentalDeposit(Long depositAmount, Integer depositOriginalPercent) {
        if (!this.rentalMode) {
            throw new IllegalStateException("rentalMode 만 보증금 설정 가능");
        }
        if (this.status.isAfterMatching()) {
            throw new IllegalStateException("결제 진행 후엔 보증금 변경 불가");
        }
        this.depositAmount = depositAmount;
        this.depositOriginalPercent = depositOriginalPercent;
    }

    

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
        
        Long buyerId = initiatorRole == InitiatorRole.buyer ? initiatorId : receiverId;
        Long sellerId = initiatorRole == InitiatorRole.buyer ? receiverId : initiatorId;

        EscrowApplication a = new EscrowApplication();
        a.linkId = linkId;
        a.entryType = EntryType.EXTERNAL;
        
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
        
        Long sellerId = initiatorId;
        Long buyerId = receiverId;

        if (chatRoomId == null) {
            throw new IllegalArgumentException("chatRoomId required for INTERNAL");
        }
        EscrowApplication a = new EscrowApplication();
        a.linkId = null;
        a.entryType = EntryType.INTERNAL;
        a.chatRoomId = chatRoomId;
        
        
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
        a.sellerId = initiatorId;   
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
        
        return a;
    }

    

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

    // 라운드 12 — 분리 입력 by-link 신청용. 생성 직후 receiverPhone 만 추가로 셋팅 (deliveryAddress 는 이미 create 시점에 셋팅됨).
    public void attachReceiverPhone(String receiverPhone) {
        if (receiverPhone != null && !receiverPhone.isBlank()) {
            this.receiverPhone = receiverPhone;
        }
    }

    // 라운드 12 — seller 가 [물품 인계] 확인. 진행중 상태에서만 호출. 상태 영향 X, 타임스탬프 + 알림용.
    public void confirmHandoverBySeller(Long sellerId) {
        if (this.status != EscrowApplicationStatus.진행중) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        if (!this.sellerId.equals(sellerId)) {
            throw new BusinessException(ErrorCode.ESCROW_FORBIDDEN);
        }
        if (this.handoverConfirmedBySellerAt != null) {
            return; // idempotent
        }
        this.handoverConfirmedBySellerAt = LocalDateTime.now();
    }

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

    
    public void markReceiverPaid() {
        if (this.status != EscrowApplicationStatus.결제대기) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        if (this.receiverPaidAt != null) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        this.receiverPaidAt = LocalDateTime.now();
        
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

    
    private void tryAdvanceToConfirmed() {
        boolean initiatorOk = this.initiatorShare == 0 || this.initiatorPaidAt != null;
        boolean receiverOk = this.receiverShare == 0 || this.receiverPaidAt != null;
        if (initiatorOk && receiverOk) {
            this.status = EscrowApplicationStatus.결제완료;
        }
    }

    
    public void markInProgress() {
        if (this.status != EscrowApplicationStatus.결제완료) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        this.status = EscrowApplicationStatus.진행중;
    }

    
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

    
    public void markSettled() {
        // 일반(판매/나눔) 거래대행 — 진행중 → 완료. 대여는 markSettledAfterReturn 사용.
        if (this.status != EscrowApplicationStatus.진행중) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        this.status = EscrowApplicationStatus.완료;
        this.settledAt = LocalDateTime.now();
    }

    // 라운드 14 — createInternal 시 item.tradeType=대여 면 호출. forward 결제 정상 처리 후 confirmReceipt 가 settle 대신 enterUsing 호출.
    public void markAsRental() {
        if (this.rentalMode) {
            return;  // 멱등
        }
        if (this.tradeMode != TradeMode.INTERNAL) {
            throw new IllegalStateException("rental mode 는 INTERNAL escrow 만 지원");
        }
        if (this.status.isAfterMatching()) {
            throw new IllegalStateException("결제 진행 후엔 rental mode 변경 불가");
        }
        this.rentalMode = true;
    }

    // PR6 — 대여 한정. 결제/진행 전 only. rentalMode=true 일 때 종료 예정 시각을 기록.
    public void markRentalEnd(LocalDateTime endAt) {
        if (!this.rentalMode) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        if (endAt == null) {
            throw new BusinessException(ErrorCode.ESCROW_FORM_INVALID);
        }
        if (this.status.isAfterMatching()) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        this.rentalEndAt = endAt;
    }

    // V43 — 대여 한정. start <= end 검증, status.afterMatching 전까지만 변경 허용.
    public void markRentalStart(LocalDateTime startAt) {
        if (!this.rentalMode) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        if (startAt == null) {
            throw new BusinessException(ErrorCode.ESCROW_FORM_INVALID);
        }
        if (this.status.isAfterMatching()) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        if (this.rentalEndAt != null && !startAt.isBefore(this.rentalEndAt)) {
            throw new BusinessException(ErrorCode.ESCROW_FORM_INVALID);
        }
        this.rentalStartAt = startAt;
    }

    // 라운드 14 — 대여 한정. confirmReceipt 후 enterUsing 으로 분기 (settle X).
    public void enterUsing() {
        if (!this.rentalMode) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);  // 일반 거래는 settle 사용
        }
        if (!this.status.canEnterUsing()) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        this.status = EscrowApplicationStatus.사용중;
        this.usingStartedAt = LocalDateTime.now();
    }

    // 라운드 14 — 대여 한정. buyer 가 [반납요청] 누른 시점. 사용중 → 반납중. return delivery 모집 시작 트리거 (service).
    public void requestReturnByBuyer(Long requesterId) {
        if (!this.rentalMode) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        if (!this.buyerId.equals(requesterId)) {
            throw new BusinessException(ErrorCode.ESCROW_FORBIDDEN);
        }
        if (!this.status.canRequestReturn()) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        this.status = EscrowApplicationStatus.반납중;
        this.returnRequestedAt = LocalDateTime.now();
    }

    // PR6 — 시스템 자동 호출. 권한 가드 없음. 사용중 상태만 반납중으로 전이.
    public void autoRequestReturn(LocalDateTime now) {
        if (!this.rentalMode) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        if (now == null) {
            throw new BusinessException(ErrorCode.ESCROW_FORM_INVALID);
        }
        if (!this.status.canRequestReturn()) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        this.status = EscrowApplicationStatus.반납중;
        this.returnRequestedAt = now;
    }

    // 라운드 14 — 대여 한정. seller [회신확인] = 거래완료. return delivery 완료 + seller 도착 후 호출.
    public void markSettledAfterReturn() {
        if (!this.rentalMode) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        if (!this.status.canConfirmReturn()) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        this.status = EscrowApplicationStatus.완료;
        this.settledAt = LocalDateTime.now();
    }

    // PR7 라운드 14 — 사용중 단계 한쪽이 [취소 요청]. 참여자만, 사용중 + 기존 요청 없을 때.
    public void requestCancelDuringUsing(Long requesterId, String reason) {
        if (!this.rentalMode) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        if (this.status != EscrowApplicationStatus.사용중) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        if (!isParticipant(requesterId)) {
            throw new BusinessException(ErrorCode.ESCROW_FORBIDDEN);
        }
        if (this.cancelRequestedBy != null) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);  // 이미 요청 중
        }
        if (reason != null && reason.length() > 500) {
            throw new IllegalArgumentException("cancelReason 은 500자 이하여야 합니다");
        }
        this.cancelRequestedBy = requesterId;
        this.cancelRequestedAt = LocalDateTime.now();
        this.cancelReason = reason;
    }

    // PR7 — 다른 참여자가 [취소 동의]. 사용중 + 요청자 != 호출자. 취소 status 전이.
    public void confirmCancelDuringUsing(Long requesterId) {
        if (!this.rentalMode) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        if (this.status != EscrowApplicationStatus.사용중) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        if (this.cancelRequestedBy == null) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);  // 요청 없음
        }
        if (!isParticipant(requesterId)) {
            throw new BusinessException(ErrorCode.ESCROW_FORBIDDEN);
        }
        if (this.cancelRequestedBy.equals(requesterId)) {
            throw new BusinessException(ErrorCode.ESCROW_FORBIDDEN);  // 요청자가 본인 confirm 불가
        }
        this.status = EscrowApplicationStatus.취소;
        this.cancelledBy = requesterId;  // confirm 한 쪽으로 기록 — 합의 완성한 사람
    }

    // PR7 — 요청자가 본인 요청 [철회]. 사용중 + 본인이 요청자.
    public void withdrawCancelRequest(Long requesterId) {
        if (!this.rentalMode) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        if (this.status != EscrowApplicationStatus.사용중) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        if (this.cancelRequestedBy == null || !this.cancelRequestedBy.equals(requesterId)) {
            throw new BusinessException(ErrorCode.ESCROW_FORBIDDEN);
        }
        this.cancelRequestedBy = null;
        this.cancelRequestedAt = null;
        this.cancelReason = null;
    }

    
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

    
    public boolean isParticipant(Long userId) {
        return this.initiatorId.equals(userId) || this.receiverId.equals(userId);
    }
}
