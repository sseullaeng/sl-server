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

/**
 * Delivery Aggregate Root. V8 스키마 {@code deliveries} 매핑.
 *
 * <p>가이드 §1 4축 중 "배달대행" 도메인. 요청자가 등록 → 라이더 수락 → 픽업 → 배송 → 정산.
 * 정산 시 요청자 포인트 차감 + 라이더 적립을 ApplicationService 가 한 트랜잭션으로 처리.
 * 본 Aggregate 는 단일 요청의 상태 머신만 책임. 수락 race 는 conditional UPDATE 가 가드.</p>
 *
 * <p>Setter 없음. 상태 변경은 명시 비즈니스 메서드만.</p>
 */
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

    /**
     * 라이더가 수락 — 모집중 → 수락. 본인이 등록한 요청은 수락 거부.
     *
     * <p>주의: race 가드는 ApplicationService 의 conditional UPDATE 가 1차 — 본 메서드는 정상 흐름에서만
     * 호출되며 본인 거래만 추가로 차단한다.</p>
     */
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

    /** 라이더 픽업 — 수락 → 배송중. 라이더 본인만 호출 가능 (호출자 검증). */
    public void markPickedUp(LocalDateTime now) {
        if (!status.canPickup()) {
            throw new BusinessException(ErrorCode.DELIVERY_INVALID_STATE);
        }
        this.status = DeliveryStatus.배송중;
        this.pickedUpAt = now;
    }

    /** 라이더 배송 완료 — 배송중 → 배송완료. */
    public void markDelivered(LocalDateTime now) {
        if (!status.canDeliver()) {
            throw new BusinessException(ErrorCode.DELIVERY_INVALID_STATE);
        }
        this.status = DeliveryStatus.배송완료;
        this.deliveredAt = now;
    }

    /**
     * 요청자 정산 확인 — 배송완료 → 정산완료. 포인트 이동(차감/적립)은 ApplicationService 가
     * 같은 트랜잭션 안에서 별도 호출.
     */
    public void markSettled(LocalDateTime now) {
        if (!status.canSettle()) {
            throw new BusinessException(ErrorCode.DELIVERY_INVALID_STATE);
        }
        this.status = DeliveryStatus.정산완료;
        this.completedAt = now;
    }

    /** 요청자 취소 — 모집중만. */
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
