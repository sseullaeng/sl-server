package com.sseulang.domain.overdue.domain;

import com.sseulang.global.common.BaseEntity;
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

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "overdue_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OverdueRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "escrow_application_id", nullable = false, unique = true)
    private Long escrowApplicationId;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "deposit_amount", nullable = false)
    private long depositAmount;

    @Column(name = "rental_end_at", nullable = false)
    private LocalDateTime rentalEndAt;

    @Column(name = "overdue_started_at", nullable = false)
    private LocalDateTime overdueStartedAt;

    @Column(name = "overdue_days", nullable = false)
    private int overdueDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "phase", nullable = false, length = 20)
    private OverduePhase phase;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OverdueStatus status;

    @Column(name = "deposit_forfeited_amount", nullable = false)
    private long depositForfeitedAmount;

    @Column(name = "extra_debt_amount", nullable = false)
    private long extraDebtAmount;

    @Column(name = "account_suspended_at")
    private LocalDateTime accountSuspendedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "legal_action", nullable = false, length = 30)
    private OverdueLegalAction legalAction;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolution_note", length = 1000)
    private String resolutionNote;

    public static OverdueRecord create(
            Long escrowApplicationId,
            Long buyerId,
            Long sellerId,
            long depositAmount,
            LocalDateTime rentalEndAt,
            LocalDateTime overdueStartedAt
    ) {
        validatePositiveId(escrowApplicationId, "escrowApplicationId");
        validatePositiveId(buyerId, "buyerId");
        validatePositiveId(sellerId, "sellerId");
        if (buyerId.equals(sellerId)) {
            throw new IllegalArgumentException("buyerId 와 sellerId 는 달라야 합니다");
        }
        if (depositAmount < 0) {
            throw new IllegalArgumentException("depositAmount 는 음수가 될 수 없습니다");
        }
        if (rentalEndAt == null) {
            throw new IllegalArgumentException("rentalEndAt 은 필수입니다");
        }
        if (overdueStartedAt == null) {
            throw new IllegalArgumentException("overdueStartedAt 은 필수입니다");
        }
        if (overdueStartedAt.isBefore(rentalEndAt)) {
            throw new IllegalArgumentException("overdueStartedAt 은 rentalEndAt 이후여야 합니다");
        }

        OverdueRecord record = new OverdueRecord();
        record.escrowApplicationId = escrowApplicationId;
        record.buyerId = buyerId;
        record.sellerId = sellerId;
        record.depositAmount = depositAmount;
        record.rentalEndAt = rentalEndAt;
        record.overdueStartedAt = overdueStartedAt;
        record.overdueDays = 0;
        record.phase = OverduePhase.PHASE_1;
        record.status = OverdueStatus.진행중;
        record.depositForfeitedAmount = 0;
        record.extraDebtAmount = 0;
        record.legalAction = OverdueLegalAction.NONE;
        return record;
    }

    public void advanceDay(LocalDateTime now, OverdueThresholds thresholds) {
        if (now == null) {
            throw new IllegalArgumentException("now 는 필수입니다");
        }
        if (thresholds == null) {
            throw new IllegalArgumentException("thresholds 는 필수입니다");
        }
        if (status != OverdueStatus.진행중) {
            return;
        }

        int newDays = (int) Duration.between(overdueStartedAt, now).toDays() + 1;
        if (newDays <= overdueDays) {
            return;
        }

        LateFeeResult fee = LateFeeCalculator.calculate(
                depositAmount,
                newDays,
                thresholds.phase2DailyRatePercent()
        );
        this.overdueDays = newDays;
        this.depositForfeitedAmount = fee.forfeited();
        this.extraDebtAmount = fee.extraDebt();
        this.phase = decidePhase(thresholds);
    }

    public void markAccountSuspended(LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("now 는 필수입니다");
        }
        if (status != OverdueStatus.진행중) {
            return;
        }
        if (this.accountSuspendedAt == null) {
            this.accountSuspendedAt = now;
        }
        this.phase = OverduePhase.PHASE_3;
    }

    public void markLegalAction(OverdueLegalAction action) {
        if (action == null || action == OverdueLegalAction.NONE) {
            throw new IllegalArgumentException("법적 조치 action 은 NONE 이 될 수 없습니다");
        }
        this.legalAction = action;
        this.phase = OverduePhase.PHASE_4;
        this.status = OverdueStatus.법적조치중;
    }

    public void markResolved(LocalDateTime now, String note) {
        if (now == null) {
            throw new IllegalArgumentException("now 는 필수입니다");
        }
        if (note != null && note.length() > 1000) {
            throw new IllegalArgumentException("resolutionNote 는 1000자를 초과할 수 없습니다");
        }
        this.resolvedAt = now;
        this.resolutionNote = note;
        this.status = extraDebtAmount == 0 ? OverdueStatus.종료 : OverdueStatus.정산완료;
    }

    public long remainingDepositAmount() {
        return depositAmount - depositForfeitedAmount;
    }

    public boolean shouldSuspendAccount(OverdueThresholds thresholds) {
        if (thresholds == null) {
            throw new IllegalArgumentException("thresholds 는 필수입니다");
        }
        long totalDebt = Math.addExact(depositForfeitedAmount, extraDebtAmount);
        return totalDebt >= thresholds.phase3AmountKrw()
                || overdueDays >= thresholds.phase3DaysThreshold();
    }

    public boolean shouldEnterLegalAction(OverdueThresholds thresholds, LocalDateTime now) {
        if (thresholds == null) {
            throw new IllegalArgumentException("thresholds 는 필수입니다");
        }
        if (now == null) {
            throw new IllegalArgumentException("now 는 필수입니다");
        }
        if (accountSuspendedAt == null) {
            return false;
        }
        return Duration.between(accountSuspendedAt, now).toDays() >= thresholds.phase4DaysAfterSuspend();
    }

    private OverduePhase decidePhase(OverdueThresholds thresholds) {
        if (legalAction != OverdueLegalAction.NONE) {
            return OverduePhase.PHASE_4;
        }
        if (accountSuspendedAt != null) {
            return OverduePhase.PHASE_3;
        }
        if (overdueDays > 7) {
            return OverduePhase.PHASE_2;
        }
        return OverduePhase.PHASE_1;
    }

    private static void validatePositiveId(Long id, String name) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(name + " 는 양수여야 합니다");
        }
    }
}
