package com.sseulang.domain.overdue.application;

import com.sseulang.domain.escrow.application.EscrowOverdueQueryService;
import com.sseulang.domain.escrow.application.dto.EscrowOverdueSnapshot;
import com.sseulang.domain.notification.application.NotificationApplicationService;
import com.sseulang.domain.notification.domain.NotificationType;
import com.sseulang.domain.overdue.domain.OverduePhase;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class OverdueApplicationService {

    private static final Logger log = LoggerFactory.getLogger(OverdueApplicationService.class);

    // Phase 3 자동 정지 시 부여 일수. 이 값 동안 채무 미해소면 cumulativeSuspendDays 누적되어
    // 자연스럽게 200일 자동 탈퇴 트리거에 합류 (AutoWithdrawalScheduler).
    private static final int PHASE3_AUTO_SUSPEND_DAYS = 30;

    private final OverdueRecordRepository overdueRecordRepository;
    private final EscrowOverdueQueryService escrowOverdueQueryService;
    private final UserApplicationService userApplicationService;
    private final PointApplicationService pointApplicationService;
    private final NotificationApplicationService notificationApplicationService;
    private final Clock clock;
    private final OverdueThresholds thresholds;

    public OverdueApplicationService(
            OverdueRecordRepository overdueRecordRepository,
            EscrowOverdueQueryService escrowOverdueQueryService,
            UserApplicationService userApplicationService,
            PointApplicationService pointApplicationService,
            NotificationApplicationService notificationApplicationService,
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
        this.notificationApplicationService = notificationApplicationService;
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
                        notifyResolvedByReturn(record, remainingDeposit);
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
        OverduePhase prevPhase = record.getPhase();

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

        // Day 1 신규 감지 알림
        if (prevDays == 0 && record.getOverdueDays() >= 1) {
            notifyBuyer(record,
                    "[연체] 반납 기한 초과",
                    "반납 기한이 지났습니다. 1일차 보증금 30% 차감이 적용되었습니다.");
        }

        // Phase 1 → Phase 2 전이 알림 (Day 8)
        if (prevPhase == OverduePhase.PHASE_1 && record.getPhase() == OverduePhase.PHASE_2) {
            notifyBuyer(record,
                    "[연체] 보증금 초과 채무 누적",
                    "보증금이 모두 차감되었습니다. 8일차부터 보증금의 "
                            + thresholds.phase2DailyRatePercent() + "%씩 추가 채무가 매일 누적됩니다.");
        }

        // Phase 3 자동 정지 트리거
        if (record.shouldSuspendAccount(thresholds) && record.getAccountSuspendedAt() == null) {
            userApplicationService.adminAutoSuspend(
                    record.getBuyerId(),
                    PHASE3_AUTO_SUSPEND_DAYS,
                    "연체 임계값 도달 (recordId=" + record.getId() + ")"
            );
            record.markAccountSuspended(now);
            notifyBuyer(record,
                    "[정지] 연체로 인한 계정 정지",
                    "연체 임계값 초과로 계정이 " + PHASE3_AUTO_SUSPEND_DAYS
                            + "일 정지되었습니다. 채무 해소 후 관리자 검토를 요청하세요.");
            log.warn("[overdue] Phase 3 — 계정 자동 정지 recordId={} buyerId={} totalDebt={}",
                    record.getId(), record.getBuyerId(),
                    record.getDepositForfeitedAmount() + record.getExtraDebtAmount());
        }

        // Phase 4 진입 (Phase 3 정지 후 N일 경과)
        if (record.shouldEnterLegalAction(thresholds, now) && record.getPhase() != OverduePhase.PHASE_4) {
            // Note: PHASE_4 전이는 record.markLegalAction(NONE 외) 또는 admin endpoint 로 발동.
            // 자동 트리거 시점에서는 admin 알림만 보내고 운영팀이 legal-action 결정.
            log.warn("[overdue] Phase 4 — 법적 조치 검토 필요 recordId={} buyerId={} suspendedAt={}",
                    record.getId(), record.getBuyerId(), record.getAccountSuspendedAt());
            // TODO: admin notification system 도입 시 여기서 admin 들에게 알림 broadcast.
        }

        return true;
    }

    private void notifyBuyer(OverdueRecord record, String title, String content) {
        try {
            notificationApplicationService.notify(
                    record.getBuyerId(),
                    NotificationType.시스템,
                    title,
                    content,
                    "OVERDUE",
                    record.getId()
            );
        } catch (RuntimeException e) {
            log.error("[overdue] 알림 발송 실패 recordId={} buyerId={} title={} reason={}",
                    record.getId(), record.getBuyerId(), title, e.getMessage(), e);
        }
    }

    private void notifyResolvedByReturn(OverdueRecord record, long remainingDeposit) {
        String content = remainingDeposit > 0
                ? "반납이 완료되었습니다. 잔여 보증금 " + remainingDeposit + "원이 환불되었습니다."
                : "반납이 완료되었습니다. 잔여 보증금이 없어 환불은 발생하지 않았습니다.";
        if (record.getExtraDebtAmount() > 0) {
            content += " 누적 채무 " + record.getExtraDebtAmount()
                    + "원은 다음 결제 시 우선 차감됩니다.";
        }
        notifyBuyer(record, "[정산완료] 연체 반납 처리", content);
    }
}
