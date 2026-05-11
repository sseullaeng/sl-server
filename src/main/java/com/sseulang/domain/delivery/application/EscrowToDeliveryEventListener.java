package com.sseulang.domain.delivery.application;

import com.sseulang.domain.delivery.domain.DeliveryRepository;
import com.sseulang.domain.delivery.domain.DeliveryRequest;
import com.sseulang.domain.escrow.domain.event.EscrowConfirmedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

@Component
public class EscrowToDeliveryEventListener {

    private static final Logger log = LoggerFactory.getLogger(EscrowToDeliveryEventListener.class);

    private final DeliveryRepository deliveryRepository;

    public EscrowToDeliveryEventListener(DeliveryRepository deliveryRepository) {
        this.deliveryRepository = deliveryRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onEscrowConfirmed(EscrowConfirmedEvent event) {
        try {
            DeliveryRequest delivery = DeliveryRequest.createFromEscrow(
                    event.requesterId(),
                    event.escrowApplicationId(),
                    event.pickupAddress(),
                    event.deliveryAddress(),
                    event.itemDescription(),
                    event.deliveryFee(),
                    LocalDateTime.now()
            );
            DeliveryRequest saved = deliveryRepository.save(delivery);
            log.info("[escrow→delivery] delivery 모집중 자동 생성 — escrowApplicationId={} deliveryId={}",
                    event.escrowApplicationId(), saved.getId());
        } catch (RuntimeException e) {
            
            log.error("[escrow→delivery] delivery 생성 실패 — escrowApplicationId={} reason={}",
                    event.escrowApplicationId(), e.getMessage(), e);
        }
    }
}
