package com.sseulang.domain.transaction.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

// B-6: 대여 [반납요청] 후 7일 동안 seller 무회신 시 자동 거래완료 + 양측 알림.
// row 단위 새 tx — 한 건 실패가 다른 건 롤백시키지 않음 (Codex 게이트 2 #3).
@Component
public class TransactionReturnScheduler {

    private static final Logger log = LoggerFactory.getLogger(TransactionReturnScheduler.class);

    private final TransactionApplicationService transactionService;
    private final Duration overdueAfter;

    public TransactionReturnScheduler(
            TransactionApplicationService transactionService,
            @Value("${app.transaction.return.auto-complete-after-hours:168}") long overdueAfterHours
    ) {
        this.transactionService = transactionService;
        this.overdueAfter = Duration.ofHours(overdueAfterHours);
    }

    @Scheduled(fixedDelayString = "${app.transaction.return.scan-fixed-delay-millis:3600000}",
            initialDelayString = "120000")
    public void scan() {
        List<Long> candidates;
        try {
            candidates = transactionService.findOverdueReturnIds(overdueAfter);
        } catch (Exception e) {
            log.warn("[return-auto-complete] 후보 조회 실패", e);
            return;
        }
        if (candidates.isEmpty()) return;

        int success = 0;
        int skipped = 0;
        for (Long txId : candidates) {
            try {
                transactionService.autoCompleteSingleReturn(txId);
                success++;
            } catch (Exception e) {
                // 한 건 실패는 격리 — 다른 건 계속 처리. 상태 변경 race 등 정상 케이스 포함.
                skipped++;
                log.debug("[return-auto-complete] txId={} 처리 스킵: {}", txId, e.getMessage());
            }
        }
        log.info("[return-auto-complete] 후보 {}건 — 성공 {} / 스킵 {} (threshold={})",
                candidates.size(), success, skipped, overdueAfter);
    }
}
