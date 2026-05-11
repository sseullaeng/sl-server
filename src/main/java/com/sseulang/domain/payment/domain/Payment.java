package com.sseulang.domain.payment.domain;

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
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "escrow_application_id")
    private Long escrowApplicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", length = 30)
    private PaymentMethod method;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "payment_key", length = 200)
    private String paymentKey;

    @Column(name = "merchant_uid", nullable = false, length = 100, unique = true)
    private String merchantUid;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "fail_reason", length = 500)
    private String failReason;

    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse;

    

    public static Payment startCharge(Long userId, String merchantUid, long amount, Long escrowApplicationId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId 는 양수여야 합니다");
        }
        if (merchantUid == null || merchantUid.isBlank()) {
            throw new IllegalArgumentException("merchantUid 는 비어있을 수 없습니다");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount 는 양수여야 합니다");
        }
        Payment p = new Payment();
        p.userId = userId;
        p.transactionId = null;
        p.escrowApplicationId = escrowApplicationId;
        p.paymentType = PaymentType.충전;
        p.amount = amount;
        p.merchantUid = merchantUid;
        p.status = PaymentStatus.대기;
        return p;
    }

    
    public static Payment startCharge(Long userId, String merchantUid, long amount) {
        return startCharge(userId, merchantUid, amount, null);
    }

    

    public boolean markAsPaid(String paymentKey, PaymentMethod method, LocalDateTime paidAt, String rawResponse) {
        if (status == PaymentStatus.완료) {
            return false;  
        }
        if (!status.canConfirm()) {
            throw new BusinessException(ErrorCode.PAYMENT_DUPLICATED);
        }
        if (paymentKey == null || paymentKey.isBlank()) {
            throw new IllegalArgumentException("paymentKey 는 필수입니다");
        }
        if (paidAt == null) {
            throw new IllegalArgumentException("paidAt 는 필수입니다");
        }
        this.paymentKey = paymentKey;
        this.method = method;
        this.paidAt = paidAt;
        this.rawResponse = rawResponse;
        this.status = PaymentStatus.완료;
        return true;
    }

    public void markAsFailed(String reason) {
        if (status.isPaid()) {
            throw new BusinessException(ErrorCode.PAYMENT_DUPLICATED);
        }
        this.status = PaymentStatus.실패;
        this.failReason = reason;
    }

    
    public void verifyAmount(long confirmedAmount) {
        if (confirmedAmount != this.amount) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
    }

    public boolean isOwnedBy(Long userId) {
        return userId != null && userId.equals(this.userId);
    }
}
