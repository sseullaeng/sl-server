package com.sseulang.domain.delivery.domain.event;

/**
 * 라이더가 배송완료 처리한 시점 발행. Escrow Mode A (EXTERNAL) 거래의 자동 정산 트리거.
 *
 * @param deliveryId            완료된 배달 id
 * @param escrowApplicationId   거래대행 연결된 application id (NULL = 일반 배달, NOT NULL = escrow 자동 정산 대상)
 */
public record DeliveryDeliveredEvent(
        Long deliveryId,
        Long escrowApplicationId
) {
}
