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

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "escrow_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EscrowLink extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "link_token", nullable = false, length = 36, updatable = false)
    private String linkToken;

    @Column(name = "initiator_id", nullable = false, updatable = false)
    private Long initiatorId;

    @Column(name = "receiver_id")
    private Long receiverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "initiator_role", nullable = false, length = 10, updatable = false)
    private InitiatorRole initiatorRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_payer", nullable = false, length = 10, updatable = false)
    private FeePayer feePayer;

    @Enumerated(EnumType.STRING)
    @Column(name = "trade_mode", nullable = false, length = 20, updatable = false)
    private TradeMode tradeMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EscrowLinkStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    
    public static EscrowLink create(
            Long initiatorId,
            InitiatorRole initiatorRole,
            FeePayer feePayer,
            TradeMode tradeMode,
            int expiryHours
    ) {
        if (initiatorId == null) throw new IllegalArgumentException("initiatorId required");
        if (initiatorRole == null) throw new IllegalArgumentException("initiatorRole required");
        if (feePayer == null) throw new IllegalArgumentException("feePayer required");
        if (tradeMode == null) throw new IllegalArgumentException("tradeMode required");
        if (expiryHours <= 0) throw new IllegalArgumentException("expiryHours > 0");

        EscrowLink link = new EscrowLink();
        link.linkToken = UUID.randomUUID().toString();
        link.initiatorId = initiatorId;
        link.initiatorRole = initiatorRole;
        link.feePayer = feePayer;
        link.tradeMode = tradeMode;
        link.status = EscrowLinkStatus.대기;
        link.expiresAt = LocalDateTime.now().plusHours(expiryHours);
        return link;
    }

    
    public void claimByReceiver(Long receiverId) {
        if (receiverId == null) {
            throw new IllegalArgumentException("receiverId required");
        }
        if (this.initiatorId.equals(receiverId)) {
            throw new BusinessException(ErrorCode.ESCROW_SELF_NOT_ALLOWED);
        }
        if (this.status != EscrowLinkStatus.대기) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        if (LocalDateTime.now().isAfter(this.expiresAt)) {
            throw new BusinessException(ErrorCode.ESCROW_LINK_EXPIRED);
        }
        if (this.receiverId != null && !this.receiverId.equals(receiverId)) {
            throw new BusinessException(ErrorCode.ESCROW_LINK_ALREADY_TAKEN);
        }
        this.receiverId = receiverId;
    }

    
    public void markAsCompleted() {
        if (this.status != EscrowLinkStatus.대기) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        this.status = EscrowLinkStatus.완료;
    }

    public void markAsExpired() {
        if (this.status != EscrowLinkStatus.대기) return;
        this.status = EscrowLinkStatus.만료;
    }

    public void markAsCancelled() {
        if (this.status != EscrowLinkStatus.대기) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        this.status = EscrowLinkStatus.취소;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }
}
