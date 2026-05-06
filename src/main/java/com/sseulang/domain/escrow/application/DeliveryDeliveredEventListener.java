package com.sseulang.domain.escrow.application;

import com.sseulang.domain.delivery.domain.event.DeliveryDeliveredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Delivery 배송완료 → Escrow Mode A 자동 정산 트리거 (게이트 1 round 1 — Warning).
 *
 * <p>{@code AFTER_COMMIT} phase — Delivery markDelivered 트랜잭션 commit 후 별도 트랜잭션
 * (REQUIRES_NEW) 으로 escrow settle. escrow_application_id NULL = 일반 배달이라 무시.</p>
 */
@Component
public class DeliveryDeliveredEventListener {

    private static final Logger log = LoggerFactory.getLogger(DeliveryDeliveredEventListener.class);

    private final EscrowApplicationService escrowService;

    public DeliveryDeliveredEventListener(EscrowApplicationService escrowService) {
        this.escrowService = escrowService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDeliveryDelivered(DeliveryDeliveredEvent event) {
        if (event.escrowApplicationId() == null) {
            return;  // 일반 배달 — escrow 정산 무관
        }
        try {
            escrowService.settleAfterDelivery(event.escrowApplicationId());
        } catch (RuntimeException e) {
            // 정산 실패 시 escrow 는 진행중 상태 유지. 운영자 수동 복구.
            log.error("[escrow-settle] Mode A 자동 정산 실패 — escrowApplicationId={} reason={}",
                    event.escrowApplicationId(), e.getMessage(), e);
        }
    }
}
