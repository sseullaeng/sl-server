package com.sseulang.domain.delivery.domain;

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
@Table(name = "deliveries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryRequest extends BaseEntity {

    private static final int MAX_ADDRESS = 255;
    private static final int MAX_DESCRIPTION = 255;
    private static final int MAX_MEMO = 500;
    private static final int MAX_CANCEL_REASON = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Column(name = "rider_id")
    private Long riderId;

    @Column(name = "pickup_address", nullable = false, length = MAX_ADDRESS)
    private String pickupAddress;

    @Column(name = "dropoff_address", nullable = false, length = MAX_ADDRESS)
    private String dropoffAddress;

    @Column(name = "item_description", nullable = false, length = MAX_DESCRIPTION)
    private String itemDescription;

    @Column(name = "fee", nullable = false)
    private long fee;

    @Column(name = "requested_deadline")
    private LocalDateTime requestedDeadline;

    @Column(name = "memo", length = MAX_MEMO)
    private String memo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DeliveryStatus status;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "picked_up_at")
    private LocalDateTime pickedUpAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "cancel_reason", length = MAX_CANCEL_REASON)
    private String cancelReason;

    
    @Column(name = "escrow_application_id")
    private Long escrowApplicationId;

    // 라운드 14 — 대여 거래대행 양방향. FORWARD=seller→buyer, RETURN=buyer→seller (반환).
    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private DeliveryDirection direction = DeliveryDirection.FORWARD;

    public static DeliveryRequest createFromEscrow(
            Long requesterId,
            Long escrowApplicationId,
            String pickupAddress,
            String dropoffAddress,
            String itemDescription,
            long fee,
            LocalDateTime now
    ) {
        return createFromEscrow(requesterId, escrowApplicationId,
                pickupAddress, dropoffAddress, itemDescription, fee, DeliveryDirection.FORWARD, now);
    }

    public static DeliveryRequest createFromEscrow(
            Long requesterId,
            Long escrowApplicationId,
            String pickupAddress,
            String dropoffAddress,
            String itemDescription,
            long fee,
            DeliveryDirection direction,
            LocalDateTime now
    ) {
        DeliveryRequest d = create(requesterId, pickupAddress, dropoffAddress, itemDescription,
                fee, null, null, now);
        d.escrowApplicationId = escrowApplicationId;
        d.direction = direction == null ? DeliveryDirection.FORWARD : direction;
        return d;
    }

    public static DeliveryRequest create(
            Long requesterId,
            String pickupAddress,
            String dropoffAddress,
            String itemDescription,
            long fee,
            LocalDateTime requestedDeadline,
            String memo,
            LocalDateTime now
    ) {
        if (requesterId == null || requesterId <= 0) {
            throw new IllegalArgumentException("requesterId 는 양수여야 합니다");
        }
        if (fee <= 0) {
            throw new IllegalArgumentException("fee 는 양수여야 합니다");
        }
        if (now == null) {
            throw new IllegalArgumentException("now 는 필수입니다");
        }
        if (requestedDeadline != null && !requestedDeadline.isAfter(now)) {
            throw new IllegalArgumentException("requestedDeadline 은 현재 시각 이후여야 합니다");
        }
        validateText(pickupAddress, "pickupAddress", MAX_ADDRESS);
        validateText(dropoffAddress, "dropoffAddress", MAX_ADDRESS);
        validateText(itemDescription, "itemDescription", MAX_DESCRIPTION);
        if (memo != null && memo.length() > MAX_MEMO) {
            throw new IllegalArgumentException("memo 는 " + MAX_MEMO + "자 이하여야 합니다");
        }

        DeliveryRequest d = new DeliveryRequest();
        d.requesterId = requesterId;
        d.pickupAddress = pickupAddress;
        d.dropoffAddress = dropoffAddress;
        d.itemDescription = itemDescription;
        d.fee = fee;
        d.requestedDeadline = requestedDeadline;
        d.memo = memo;
        d.status = DeliveryStatus.모집중;
        d.requestedAt = now;
        return d;
    }

    

    public void acceptBy(Long riderId, LocalDateTime now) {
        if (riderId == null || riderId <= 0) {
            throw new IllegalArgumentException("riderId 는 양수여야 합니다");
        }
        if (riderId.equals(this.requesterId)) {
            throw new BusinessException(ErrorCode.DELIVERY_SELF_NOT_ALLOWED);
        }
        if (!status.canAccept()) {
            throw new BusinessException(ErrorCode.DELIVERY_INVALID_STATE);
        }
        this.riderId = riderId;
        this.status = DeliveryStatus.수락;
        this.acceptedAt = now;
    }

    
    public void markPickedUp(LocalDateTime now) {
        if (!status.canPickup()) {
            throw new BusinessException(ErrorCode.DELIVERY_INVALID_STATE);
        }
        this.status = DeliveryStatus.배송중;
        this.pickedUpAt = now;
    }

    
    public void markDelivered(LocalDateTime now) {
        if (!status.canDeliver()) {
            throw new BusinessException(ErrorCode.DELIVERY_INVALID_STATE);
        }
        this.status = DeliveryStatus.배송완료;
        this.deliveredAt = now;
    }

    

    public void markSettled(LocalDateTime now) {
        if (!status.canSettle()) {
            throw new BusinessException(ErrorCode.DELIVERY_INVALID_STATE);
        }
        this.status = DeliveryStatus.정산완료;
        this.completedAt = now;
    }

    
    public void cancelByRequester(LocalDateTime now, String reason) {
        if (!status.canRequesterCancel()) {
            throw new BusinessException(ErrorCode.DELIVERY_INVALID_STATE);
        }
        if (reason != null && reason.length() > MAX_CANCEL_REASON) {
            throw new IllegalArgumentException("cancelReason 은 " + MAX_CANCEL_REASON + "자 이하여야 합니다");
        }
        this.status = DeliveryStatus.취소;
        this.canceledAt = now;
        this.cancelReason = reason;
    }

    public boolean isRequester(Long userId) {
        return userId != null && userId.equals(this.requesterId);
    }

    public boolean isRider(Long userId) {
        return userId != null && riderId != null && userId.equals(this.riderId);
    }

    public boolean isParticipant(Long userId) {
        return isRequester(userId) || isRider(userId);
    }

    private static void validateText(String value, String name, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 는 필수입니다");
        }
        if (value.length() > max) {
            throw new IllegalArgumentException(name + " 는 " + max + "자 이하여야 합니다");
        }
    }
}
