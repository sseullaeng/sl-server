package com.sseulang.domain.transaction.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

// B-6: 대여 [반납요청] 후 7일 동안 seller 무회신 시 자동 거래완료 + 양측 알림.
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
    public void autoCompleteOverdueReturns() {
        try {
            int affected = transactionService.autoCompleteOverdueReturns(overdueAfter);
            if (affected > 0) {
                log.info("[return-auto-complete] {}건 자동 거래완료 (threshold={})", affected, overdueAfter);
            }
        } catch (Exception e) {
            log.warn("[return-auto-complete] scan 실패", e);
        }
    }
}
