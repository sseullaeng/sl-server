package com.sseulang.domain.transaction.application;

import com.sseulang.domain.notification.application.NotificationApplicationService;
import com.sseulang.domain.notification.domain.NotificationType;
import com.sseulang.domain.transaction.domain.event.TransactionCanceledEvent;
import com.sseulang.domain.transaction.domain.event.TransactionHandoverConfirmedEvent;
import com.sseulang.domain.transaction.domain.event.TransactionReceiveConfirmedEvent;
import com.sseulang.domain.transaction.domain.event.TransactionReservedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 라운드 11 — 거래 단계별 알림 발송. {@code AFTER_COMMIT} phase: TransactionApplicationService 의
 * 트랜잭션 커밋 후 실행 (롤백 시 X). linkType="TRANSACTION" + linkId=거래ID — 프론트가 클릭 시
 * /trades/{id} 이동 (B-4 합의).
 *
 * <p><b>예외 격리</b> (게이트 2 W-1): 각 listener 는 {@code REQUIRES_NEW} 별도 트랜잭션 + try/catch.
 * Mongo 저장 실패가 호출자(HTTP request 처리 스레드) 로 전파되어 거래 응답이 500 으로 끝나는 회귀 방지.
 * 알림 발송 실패는 ERROR 로깅으로만 기록 — 운영 모니터링이 처리.</p>
 */
@Component
public class TransactionNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionNotificationListener.class);

    private static final String LINK_TYPE = "TRANSACTION";

    private final NotificationApplicationService notificationService;

    public TransactionNotificationListener(NotificationApplicationService notificationService) {
        this.notificationService = notificationService;
    }

    /** 예약됨 → buyer 에게. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReserved(TransactionReservedEvent e) {
        try {
            notificationService.notify(
                    e.buyerId(),
                    NotificationType.거래,
                    "거래가 예약되었습니다",
                    "거래 #" + e.transactionId() + " 가 예약되었습니다.",
                    LINK_TYPE,
                    e.transactionId()
            );
        } catch (RuntimeException ex) {
            log.error("[tx-notify] reserved 알림 발송 실패 — txId={} buyerId={}", e.transactionId(), e.buyerId(), ex);
        }
    }

    /** 인계확인됨 → buyer 에게 인수확인 부탁. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onHandoverConfirmed(TransactionHandoverConfirmedEvent e) {
        try {
            notificationService.notify(
                    e.buyerId(),
                    NotificationType.거래,
                    "판매자가 인계를 확인했습니다",
                    "거래 #" + e.transactionId() + " — 인수 확인을 부탁드립니다.",
                    LINK_TYPE,
                    e.transactionId()
            );
        } catch (RuntimeException ex) {
            log.error("[tx-notify] handover 알림 발송 실패 — txId={} buyerId={}", e.transactionId(), e.buyerId(), ex);
        }
    }

    /** 인수확인됨 / 거래완료 → seller 에게 정산 완료 (+N,000P 표시). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReceiveConfirmed(TransactionReceiveConfirmedEvent e) {
        try {
            String body;
            if (e.settleAmount() > 0) {
                body = "거래 #" + e.transactionId() + " 정산 완료 (+" + formatKrw(e.settleAmount()) + "P)";
            } else {
                body = "거래 #" + e.transactionId() + " 가 완료되었습니다.";  // 나눔 거래
            }
            notificationService.notify(
                    e.sellerId(),
                    NotificationType.거래,
                    "거래 정산이 완료되었습니다",
                    body,
                    LINK_TYPE,
                    e.transactionId()
            );
        } catch (RuntimeException ex) {
            log.error("[tx-notify] receive 알림 발송 실패 — txId={} sellerId={}", e.transactionId(), e.sellerId(), ex);
        }
    }

    /** 거래취소됨 → 양쪽 참여자에게. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCanceled(TransactionCanceledEvent e) {
        String body = "거래 #" + e.transactionId() + " 가 취소되었습니다.";
        // 두 발송을 한 트랜잭션 안에서 — 한 쪽 실패해도 try/catch 격리.
        try {
            notificationService.notify(
                    e.buyerId(), NotificationType.거래,
                    "거래가 취소되었습니다", body, LINK_TYPE, e.transactionId()
            );
        } catch (RuntimeException ex) {
            log.error("[tx-notify] canceled buyer 알림 발송 실패 — txId={} buyerId={}", e.transactionId(), e.buyerId(), ex);
        }
        if (!e.buyerId().equals(e.sellerId())) {
            try {
                notificationService.notify(
                        e.sellerId(), NotificationType.거래,
                        "거래가 취소되었습니다", body, LINK_TYPE, e.transactionId()
                );
            } catch (RuntimeException ex) {
                log.error("[tx-notify] canceled seller 알림 발송 실패 — txId={} sellerId={}", e.transactionId(), e.sellerId(), ex);
            }
        }
    }

    private static String formatKrw(long amount) {
        return String.format("%,d", amount);
    }
}
