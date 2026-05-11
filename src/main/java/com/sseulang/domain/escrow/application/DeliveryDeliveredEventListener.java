package com.sseulang.domain.escrow.application;

import com.sseulang.domain.delivery.domain.event.DeliveryDeliveredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

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
            return;  
        }
        try {
            escrowService.settleAfterDelivery(event.escrowApplicationId());
        } catch (RuntimeException e) {
            
            log.error("[escrow-settle] Mode A 자동 정산 실패 — escrowApplicationId={} reason={}",
                    event.escrowApplicationId(), e.getMessage(), e);
        }
    }
}
