package com.sseulang.domain.transaction.application;

import com.sseulang.domain.notification.application.NotificationApplicationService;
import com.sseulang.domain.notification.domain.NotificationType;
import com.sseulang.domain.transaction.domain.event.TransactionAutoCompletedEvent;
import com.sseulang.domain.transaction.domain.event.TransactionCanceledEvent;
import com.sseulang.domain.transaction.domain.event.TransactionHandoverConfirmedEvent;
import com.sseulang.domain.transaction.domain.event.TransactionReceiveConfirmedEvent;
import com.sseulang.domain.transaction.domain.event.TransactionReservedEvent;
import com.sseulang.domain.transaction.domain.event.TransactionReturnRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TransactionNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionNotificationListener.class);

    private static final String LINK_TYPE = "TRANSACTION";

    private final NotificationApplicationService notificationService;

    public TransactionNotificationListener(NotificationApplicationService notificationService) {
        this.notificationService = notificationService;
    }

    
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

    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReceiveConfirmed(TransactionReceiveConfirmedEvent e) {
        try {
            String body;
            if (e.settleAmount() > 0) {
                body = "거래 #" + e.transactionId() + " 정산 완료 (+" + formatKrw(e.settleAmount()) + "P)";
            } else {
                body = "거래 #" + e.transactionId() + " 가 완료되었습니다.";  
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

    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCanceled(TransactionCanceledEvent e) {
        String body = "거래 #" + e.transactionId() + " 가 취소되었습니다.";
        
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

    // B-6: buyer 가 [반납] 요청 → seller 알림.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReturnRequested(TransactionReturnRequestedEvent e) {
        try {
            notificationService.notify(
                    e.sellerId(), NotificationType.거래,
                    "반납 요청이 도착했어요",
                    "거래 #" + e.transactionId() + " — 빌린 분이 반납했어요. 회신해 주세요.",
                    LINK_TYPE, e.transactionId()
            );
        } catch (RuntimeException ex) {
            log.error("[tx-notify] return-request 알림 발송 실패 — txId={} sellerId={}", e.transactionId(), e.sellerId(), ex);
        }
    }

    // B-6: 7일 무회신 자동 거래완료 → 양측 알림.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAutoCompleted(TransactionAutoCompletedEvent e) {
        String body = "거래 #" + e.transactionId() + " 가 7일 무회신으로 자동 완료 처리됐어요.";
        try {
            notificationService.notify(
                    e.buyerId(), NotificationType.거래,
                    "대여 거래가 자동 완료됐어요", body, LINK_TYPE, e.transactionId()
            );
        } catch (RuntimeException ex) {
            log.error("[tx-notify] auto-complete buyer 알림 실패 — txId={}", e.transactionId(), ex);
        }
        if (!e.buyerId().equals(e.sellerId())) {
            try {
                notificationService.notify(
                        e.sellerId(), NotificationType.거래,
                        "대여 거래가 자동 완료됐어요", body, LINK_TYPE, e.transactionId()
                );
            } catch (RuntimeException ex) {
                log.error("[tx-notify] auto-complete seller 알림 실패 — txId={}", e.transactionId(), ex);
            }
        }
    }

    private static String formatKrw(long amount) {
        return String.format("%,d", amount);
    }
}
