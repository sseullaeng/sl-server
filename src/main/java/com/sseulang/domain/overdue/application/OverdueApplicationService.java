package com.sseulang.domain.overdue.application;

import com.sseulang.domain.escrow.application.EscrowOverdueQueryService;
import com.sseulang.domain.escrow.application.dto.EscrowOverdueSnapshot;
import com.sseulang.domain.overdue.domain.OverdueRecord;
import com.sseulang.domain.overdue.domain.OverdueRecordRepository;
import com.sseulang.domain.overdue.domain.OverdueStatus;
import com.sseulang.domain.overdue.domain.OverdueThresholds;
import com.sseulang.domain.point.application.PointApplicationService;
import com.sseulang.domain.point.domain.PointHistoryType;
import com.sseulang.domain.point.domain.PointReferenceType;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class OverdueApplicationService {

    private final OverdueRecordRepository overdueRecordRepository;
    private final EscrowOverdueQueryService escrowOverdueQueryService;
    private final UserApplicationService userApplicationService;
    private final PointApplicationService pointApplicationService;
    private final Clock clock;
    private final OverdueThresholds thresholds;

    public OverdueApplicationService(
            OverdueRecordRepository overdueRecordRepository,
            EscrowOverdueQueryService escrowOverdueQueryService,
            UserApplicationService userApplicationService,
            PointApplicationService pointApplicationService,
            Clock clock,
            @Value("${app.overdue.phase3-amount-krw:50000}") int phase3AmountKrw,
            @Value("${app.overdue.phase3-days-threshold:14}") int phase3DaysThreshold,
            @Value("${app.overdue.phase4-days-after-suspend:7}") int phase4DaysAfterSuspend,
            @Value("${app.overdue.phase2-daily-rate-percent:20}") int phase2DailyRatePercent
    ) {
        this.overdueRecordRepository = overdueRecordRepository;
        this.escrowOverdueQueryService = escrowOverdueQueryService;
        this.userApplicationService = userApplicationService;
        this.pointApplicationService = pointApplicationService;
        this.clock = clock;
        this.thresholds = new OverdueThresholds(
                phase3AmountKrw,
                phase3DaysThreshold,
                phase4DaysAfterSuspend,
                phase2DailyRatePercent
        );
    }

    public List<Long> findOverdueCandidateEscrowIds(LocalDateTime cutoff) {
        return escrowOverdueQueryService.findOverdueCandidates(cutoff).stream()
                .filter(s -> !overdueRecordRepository.existsByEscrowApplicationId(s.id()))
                .map(EscrowOverdueSnapshot::id)
                .toList();
    }

    public List<Long> findActiveRecordIds(int limit) {
        return overdueRecordRepository.findActiveIds(limit);
    }

    @Transactional
    public boolean startOverdue(Long escrowApplicationId) {
        return startOverdue(escrowApplicationId, LocalDateTime.now(clock));
    }

    @Transactional
    public boolean startOverdue(Long escrowApplicationId, LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("now 는 필수입니다");
        }
        if (overdueRecordRepository.existsByEscrowApplicationId(escrowApplicationId)) {
            return false;
        }

        EscrowOverdueSnapshot escrow = escrowOverdueQueryService.getForOverdue(escrowApplicationId);
        OverdueRecord record = OverdueRecord.create(
                escrow.id(),
                escrow.buyerId(),
                escrow.sellerId(),
                escrow.depositAmount(),
                escrow.rentalEndAt(),
                now
        );
        OverdueRecord saved = overdueRecordRepository.save(record);
        advanceDay(saved, now);
        return true;
    }

    @Transactional
    public boolean advanceDay(Long recordId) {
        return advanceDay(recordId, LocalDateTime.now(clock));
    }

    @Transactional
    public boolean advanceDay(Long recordId, LocalDateTime now) {
        OverdueRecord record = overdueRecordRepository.findByIdForUpdate(recordId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OVERDUE_NOT_FOUND));
        return advanceDay(record, now);
    }

    @Transactional
    public boolean markResolvedByReturn(Long escrowApplicationId) {
        return markResolvedByReturn(escrowApplicationId, LocalDateTime.now(clock));
    }

    @Transactional
    public boolean markResolvedByReturn(Long escrowApplicationId, LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("now 는 필수입니다");
        }
        return overdueRecordRepository.findByEscrowApplicationIdForUpdate(escrowApplicationId)
                .map(record -> {
                    if (record.getStatus() == OverdueStatus.진행중
                            || record.getStatus() == OverdueStatus.법적조치중) {
                        long remainingDeposit = record.remainingDepositAmount();
                        if (remainingDeposit > 0) {
                            userApplicationService.refundHold(
                                    record.getBuyerId(),
                                    remainingDeposit
                            );
                        }
                        record.markResolved(now, "반납 완료");
                    }
                    return true;
                })
                .orElse(false);
    }

    private boolean advanceDay(OverdueRecord record, LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("now 는 필수입니다");
        }
        if (record.getStatus() != OverdueStatus.진행중) {
            return false;
        }

        int prevDays = record.getOverdueDays();
        long prevForfeited = record.getDepositForfeitedAmount();
        long prevDebt = record.getExtraDebtAmount();

        record.advanceDay(now, thresholds);

        if (record.getOverdueDays() <= prevDays) {
            return false;
        }

        long deltaForfeit = record.getDepositForfeitedAmount() - prevForfeited;
        long deltaDebt = record.getExtraDebtAmount() - prevDebt;

        if (deltaForfeit > 0) {
            userApplicationService.releaseHold(record.getBuyerId(), deltaForfeit);
            pointApplicationService.credit(
                    record.getSellerId(),
                    deltaForfeit,
                    PointHistoryType.연체몰수,
                    PointReferenceType.OVERDUE,
                    record.getId(),
                    "연체 보증금 몰수 — " + record.getOverdueDays() + "일차"
            );
        }

        if (deltaDebt > 0) {
            userApplicationService.incrementOverdueDebt(record.getBuyerId(), deltaDebt);
        }

        return true;
    }
}
