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

/**
 * Payment Aggregate Root. V1 스키마 {@code payments} — 충전/거래결제/환불 통합.
 *
 * <p>가이드 §4.9 멱등성: {@code merchant_uid} UNIQUE — 우리 측 주문 ID 로 중복 결제 차단.
 * 위변조 방지: 토스 confirm 후 amount 재검증.</p>
 */
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

    /**
     * 충전 시작 — status=대기. amount 양수 강제.
     * escrowApplicationId 가 NOT NULL 이면 거래대행 결제 — confirm 시 잔액 차감 + escrow 갱신 트리거.
     */
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

    /** Day 7 호환 (escrowApplicationId 없는 일반 충전). */
    public static Payment startCharge(Long userId, String merchantUid, long amount) {
        return startCharge(userId, merchantUid, amount, null);
    }

    /**
     * 토스 confirm 응답 받아 완료 처리. 멱등 — 이미 완료면 무시 (true=새로 적용, false=중복).
     *
     * <p>paymentKey / paidAt 필수 — 완료 결제인데 paid_at 비는 상태 차단 (게이트 1 round 2 Warning).
     * method 는 결제수단별 nullable 허용 (예: 일부 가상계좌는 method 없이 응답).</p>
     */
    public boolean markAsPaid(String paymentKey, PaymentMethod method, LocalDateTime paidAt, String rawResponse) {
        if (status == PaymentStatus.완료) {
            return false;  // 멱등 — 이미 완료
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

    /** 토스 응답의 amount 가 우리가 저장한 amount 와 일치하는지 검증 — 위변조 방지. */
    public void verifyAmount(long confirmedAmount) {
        if (confirmedAmount != this.amount) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
    }

    public boolean isOwnedBy(Long userId) {
        return userId != null && userId.equals(this.userId);
    }
}
