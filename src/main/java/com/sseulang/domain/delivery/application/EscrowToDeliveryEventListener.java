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

/**
 * 거래대행 (Escrow) 결제완료 → 배달대행 (Delivery) 모집중 row 자동 생성.
 * 결정 #7 P1 — Delivery 도메인 재사용 + DomainEvent 약결합.
 *
 * <p>{@code AFTER_COMMIT} phase — Escrow 트랜잭션 commit 된 후 listener 호출. 새 delivery row 는
 * 별도 트랜잭션에서 INSERT (REQUIRES_NEW) — escrow 트랜잭션 종료 후이므로 이미 분리된 흐름.</p>
 *
 * <p>5/11 시점 자동 라이더 매칭 X — dummy rider 로 운영자가 수동 PATCH /accept 호출 (X1 follow-up).</p>
 */
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
            // commit 후 실패 시 — escrow 는 결제완료 상태로 유지, delivery 만 누락. 운영자 수동 복구.
            log.error("[escrow→delivery] delivery 생성 실패 — escrowApplicationId={} reason={}",
                    event.escrowApplicationId(), e.getMessage(), e);
        }
    }
}
