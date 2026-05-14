package com.sseulang.domain.point.domain;

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
@Table(name = "point_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "point_type", nullable = false)
    private PointHistoryType pointType;

    
    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", length = 30)
    private PointReferenceType referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "overdue_record_id")
    private Long overdueRecordId;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    

    public static PointHistory recordCredit(
            Long userId,
            PointHistoryType type,
            long amount,
            long balanceAfter,
            PointReferenceType referenceType,
            Long referenceId,
            String description,
            LocalDateTime now
    ) {
        validateAmountPositive(amount);
        validateCreditType(type);
        return build(userId, type, amount, balanceAfter, referenceType, referenceId, description, now);
    }

    

    public static PointHistory recordDebit(
            Long userId,
            PointHistoryType type,
            long amount,
            long balanceAfter,
            PointReferenceType referenceType,
            Long referenceId,
            String description,
            LocalDateTime now
    ) {
        validateAmountPositive(amount);
        validateDebitType(type);
        return build(userId, type, -amount, balanceAfter, referenceType, referenceId, description, now);
    }

    private static PointHistory build(
            Long userId,
            PointHistoryType type,
            long signedAmount,
            long balanceAfter,
            PointReferenceType referenceType,
            Long referenceId,
            String description,
            LocalDateTime now
    ) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId 는 양수여야 합니다");
        }
        if (type == null) {
            throw new IllegalArgumentException("pointType 은 필수입니다");
        }
        if (balanceAfter < 0) {
            throw new IllegalArgumentException("balanceAfter 는 음수가 될 수 없습니다");
        }
        if (now == null) {
            throw new IllegalArgumentException("now 는 필수입니다");
        }
        PointHistory h = new PointHistory();
        h.userId = userId;
        h.pointType = type;
        h.amount = signedAmount;
        h.balanceAfter = balanceAfter;
        h.referenceType = referenceType;
        h.referenceId = referenceId;
        h.description = description;
        h.createdAt = now;
        return h;
    }

    private static void validateAmountPositive(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount 는 양수여야 합니다 (caller 는 절대값 전달, 부호는 factory 책임)");
        }
    }

    private static void validateCreditType(PointHistoryType type) {
        if (type != PointHistoryType.충전
                && type != PointHistoryType.판매정산
                && type != PointHistoryType.환불
                && type != PointHistoryType.배달정산
                && type != PointHistoryType.거래환불
                && type != PointHistoryType.연체몰수) {
            throw new IllegalArgumentException(
                    "recordCredit 은 충전 / 판매정산 / 환불 / 배달정산 / 거래환불 / 연체몰수 type 만 허용: " + type);
        }
    }

    private static void validateDebitType(PointHistoryType type) {
        if (type != PointHistoryType.결제
                && type != PointHistoryType.출금
                && type != PointHistoryType.환불
                && type != PointHistoryType.배달결제
                && type != PointHistoryType.거래보관
                && type != PointHistoryType.연체채무상환) {
            throw new IllegalArgumentException(
                    "recordDebit 은 결제 / 출금 / 환불 / 배달결제 / 거래보관 / 연체채무상환 type 만 허용: " + type);
        }
    }
}
