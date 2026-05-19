package com.sseulang.domain.payment.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class PaymentReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationScheduler.class);

    
    private static final int BATCH_LIMIT = 50;

    private final PaymentApplicationService paymentService;
    private final Clock clock;
    private final long staleAfterMillis;

    public PaymentReconciliationScheduler(
            PaymentApplicationService paymentService,
            Clock clock,
            @Value("${app.payment.reconcile.stale-after-millis:600000}")  
            long staleAfterMillis
    ) {
        this.paymentService = paymentService;
        this.clock = clock;
        this.staleAfterMillis = staleAfterMillis;
    }

    

    @Scheduled(fixedDelayString = "${app.payment.reconcile.fixed-delay-millis:300000}", initialDelayString = "60000")
    public void reconcile() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(Duration.ofMillis(staleAfterMillis));
        List<Long> stale = paymentService.findStalePendingIds(cutoff, BATCH_LIMIT);
        if (stale.isEmpty()) {
            return;
        }
        log.info("[reconcile] stale Payment {} 건 처리 시작 (cutoff={})", stale.size(), cutoff);
        int recovered = 0;
        for (Long id : stale) {
            try {
                if (paymentService.reconcileStalePayment(id)) {
                    recovered++;
                }
            } catch (Exception e) {
                log.error("[reconcile] payment#{} 처리 중 예외 (다음 cycle 재시도)", id, e);
            }
        }
        log.info("[reconcile] stale Payment {} 건 중 {} 건 복구", stale.size(), recovered);
    }
}
