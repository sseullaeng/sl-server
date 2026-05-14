package com.sseulang.domain.escrow.application;

import com.sseulang.global.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

// PR6: rentalEndAt 경과 후 buyer 가 [반납요청]을 누르지 않으면 자동 반납요청.
// row 단위 새 tx — 한 건 실패가 다른 건을 롤백시키지 않음.
@Component
public class EscrowReturnReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(EscrowReturnReminderScheduler.class);

    private final EscrowApplicationService service;

    public EscrowReturnReminderScheduler(EscrowApplicationService service) {
        this.service = service;
    }

    @Scheduled(
            fixedDelayString = "${app.escrow.return-reminder.scan-fixed-delay-millis:3600000}",
            initialDelayString = "180000"
    )
    public void scan() {
        List<Long> candidateIds;
        try {
            candidateIds = service.findOverdueRentalEndIds();
        } catch (Exception e) {
            log.warn("[rental-return-reminder] 후보 조회 실패", e);
            return;
        }
        if (candidateIds.isEmpty()) {
            return;
        }

        int success = 0;
        int skipped = 0;
        int failed = 0;
        for (Long applicationId : candidateIds) {
            try {
                service.autoTriggerReturn(applicationId);
                success++;
            } catch (BusinessException e) {
                skipped++;
                log.debug("[rental-return-reminder] applicationId={} 정책 스킵: {}", applicationId, e.getErrorCode());
            } catch (RuntimeException e) {
                failed++;
                log.warn("[rental-return-reminder] applicationId={} 인프라 실패", applicationId, e);
            }
        }

        log.info("[rental-return-reminder] 후보 {}건 — 성공 {} / 스킵 {} / 실패 {}",
                candidateIds.size(), success, skipped, failed);
    }
}
