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

/**
 * PointHistory Aggregate Root. V1 스키마 {@code point_histories} 매핑.
 *
 * <p>가이드 §4.8 — 모든 잔액 변동 흐름 (충전 / 결제 / 판매정산 / 출금 / 환불) 에서 적재.
 * amount 는 {@code +} (증가) / {@code -} (감소) 부호 포함. balance_after 는 변동 직후 잔액 스냅샷.</p>
 *
 * <p>Setter 없음. 정적 팩토리 {@link #record} 만 사용. created_at 은 DB DEFAULT CURRENT_TIMESTAMP 가
 * 채우지만 Aggregate 생성 시 명시 설정해 테스트/감사 일관.</p>
 */
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

    /** + 증가 / - 감소 부호 포함 */
    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", length = 30)
    private PointReferenceType referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 잔액 증가 history. caller 는 양수 amount 만 전달, 저장 amount 도 + 부호로 보존.
     * 허용 type: 충전 / 판매정산 / 환불 (buyer 환불 적립).
     */
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

    /**
     * 잔액 감소 history. caller 는 양수 amount 만 전달, 저장 amount 는 - 부호로 보존.
     * 허용 type: 결제 / 출금 / 환불 (seller 환불 차감).
     */
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
                && type != PointHistoryType.환불) {
            throw new IllegalArgumentException("recordCredit 은 충전 / 판매정산 / 환불 type 만 허용: " + type);
        }
    }

    private static void validateDebitType(PointHistoryType type) {
        if (type != PointHistoryType.결제
                && type != PointHistoryType.출금
                && type != PointHistoryType.환불) {
            throw new IllegalArgumentException("recordDebit 은 결제 / 출금 / 환불 type 만 허용: " + type);
        }
    }
}
