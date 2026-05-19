package com.sseulang.domain.delivery.application;

import com.sseulang.domain.delivery.domain.DeliveryRepository;
import com.sseulang.domain.delivery.domain.DeliveryRequest;
import com.sseulang.domain.escrow.domain.event.EscrowConfirmedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    private final DeliveryApplicationService deliveryApplicationService;
    private final Long autoAcceptRiderId;

    public EscrowToDeliveryEventListener(
            DeliveryRepository deliveryRepository,
            DeliveryApplicationService deliveryApplicationService,
            @Value("${app.delivery.auto-accept-rider-id:0}") long autoAcceptRiderId
    ) {
        this.deliveryRepository = deliveryRepository;
        this.deliveryApplicationService = deliveryApplicationService;
        this.autoAcceptRiderId = autoAcceptRiderId > 0 ? autoAcceptRiderId : null;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onEscrowConfirmed(EscrowConfirmedEvent event) {
        Long deliveryId;
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
            deliveryId = saved.getId();
            log.info("[escrow→delivery] delivery 모집중 자동 생성 — escrowApplicationId={} deliveryId={}",
                    event.escrowApplicationId(), deliveryId);
        } catch (RuntimeException e) {
            log.error("[escrow→delivery] delivery 생성 실패 — escrowApplicationId={} reason={}",
                    event.escrowApplicationId(), e.getMessage(), e);
            return;
        }

        if (autoAcceptRiderId == null) {
            return;
        }
        if (event.requesterId() != null && event.requesterId().equals(autoAcceptRiderId)) {
            log.warn("[escrow→delivery] auto-accept skip — buyer({}) == dummyRider({})",
                    event.requesterId(), autoAcceptRiderId);
            return;
        }
        try {
            deliveryApplicationService.accept(deliveryId, autoAcceptRiderId);
            log.warn("[escrow→delivery][AUTO-ACCEPT] deliveryId={} riderId={} — 시연용 더미 라이더 자동 매칭 (dev/QA 전용)",
                    deliveryId, autoAcceptRiderId);
        } catch (RuntimeException e) {
            log.error("[escrow→delivery] auto-accept 실패 — deliveryId={} riderId={} reason={}",
                    deliveryId, autoAcceptRiderId, e.getMessage(), e);
        }
    }
}
