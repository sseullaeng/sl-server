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

/**
 * 토스 결제 dangling 복구 스케줄러 (follow-up #21).
 *
 * <p>주기적으로 status=대기 + 일정 시간 경과 Payment 를 토스 lookup 으로 동기화. 시나리오:
 * <ul>
 *   <li>confirm 도중 응답 유실 / 5xx → DB 는 대기, 토스는 DONE</li>
 *   <li>markAsPaid + creditPoint 직전 commit 장애 → 동일</li>
 *   <li>webhook 도 못 받은 케이스 (토스 측 webhook 발송 실패 또는 네트워크)</li>
 * </ul>
 *
 * <p>각 Payment 는 별도 트랜잭션으로 처리 — 한 건 실패해도 다른 건 진행.
 * 스케줄 주기는 {@code app.payment.reconcile.interval-millis} (기본 5분).</p>
 */
@Component
public class PaymentReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationScheduler.class);

    /** 한 cycle 에 처리할 최대 건수 — Toss API 부하 / 트랜잭션 시간 제어. */
    private static final int BATCH_LIMIT = 50;

    private final PaymentApplicationService paymentService;
    private final Clock clock;
    private final long staleAfterMillis;

    public PaymentReconciliationScheduler(
            PaymentApplicationService paymentService,
            Clock clock,
            @Value("${app.payment.reconcile.stale-after-millis:600000}")  // 기본 10분
            long staleAfterMillis
    ) {
        this.paymentService = paymentService;
        this.clock = clock;
        this.staleAfterMillis = staleAfterMillis;
    }

    /**
     * 5분마다 실행. fixedDelay — 이전 cycle 끝난 후 5분 (overlap 차단).
     * 운영 환경에서 주기 변경은 {@code app.payment.reconcile.fixed-delay-millis} (Spring Boot 자동 바인딩 X 라
     * 본 메서드 변경 필요 — 5/6 이전 단순화).
     */
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
