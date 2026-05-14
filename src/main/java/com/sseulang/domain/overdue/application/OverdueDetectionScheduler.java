package com.sseulang.domain.overdue.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 매일 새벽 2시 KST — 신규 연체 감지 + 진행중 record advance.
 * AutoWithdrawalScheduler(1시) 와 시간 분리.
 */
@Component
public class OverdueDetectionScheduler {

    private static final Logger log = LoggerFactory.getLogger(OverdueDetectionScheduler.class);

    private static final int BATCH_LIMIT = 200;
    private static final int OVERDUE_TRIGGER_HOURS = 24;

    private final OverdueApplicationService overdueApplicationService;
    private final Clock clock;

    public OverdueDetectionScheduler(
            OverdueApplicationService overdueApplicationService,
            Clock clock
    ) {
        this.overdueApplicationService = overdueApplicationService;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
    public void run() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime cutoff = now.minusHours(OVERDUE_TRIGGER_HOURS);

        // Step 1 — 신규 연체 감지
        List<Long> candidates = overdueApplicationService.findOverdueCandidateEscrowIds(cutoff);
        if (!candidates.isEmpty()) {
            log.info("[overdue-scheduler] 신규 연체 후보 {} 건", candidates.size());
        }
        int started = 0;
        for (Long escrowId : candidates) {
            try {
                if (overdueApplicationService.startOverdue(escrowId, now)) {
                    started++;
                }
            } catch (Exception e) {
                log.error("[overdue-scheduler] startOverdue 실패 escrowApplicationId={}", escrowId, e);
            }
        }

        // Step 2 — 진행중 record 매일 +1
        List<Long> activeIds = overdueApplicationService.findActiveRecordIds(BATCH_LIMIT);
        if (!activeIds.isEmpty()) {
            log.info("[overdue-scheduler] 진행중 record {} 건 advance", activeIds.size());
        }
        int advanced = 0;
        for (Long recordId : activeIds) {
            try {
                if (overdueApplicationService.advanceDay(recordId, now)) {
                    advanced++;
                }
            } catch (Exception e) {
                log.error("[overdue-scheduler] advanceDay 실패 overdueRecordId={}", recordId, e);
            }
        }

        if (started > 0 || advanced > 0) {
            log.info("[overdue-scheduler] 완료 — 신규 {} 건 / 진행 {} 건", started, advanced);
        }
    }
}
