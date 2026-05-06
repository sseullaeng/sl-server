package com.sseulang.domain.escrow.domain.event;

/**
 * 양쪽 결제 완료 → 결제완료 상태 진입 시 발행. Delivery 도메인이 listen 해서 모집중 row 자동 INSERT
 * + 자동 라이더 매칭 (X1, 결정 #8).
 *
 * @param escrowApplicationId 결제완료된 application
 * @param pickupAddress       픽업 주소
 * @param pickupLat           픽업 위도
 * @param pickupLng           픽업 경도
 * @param deliveryAddress     도착 주소
 * @param deliveryLat         도착 위도
 * @param deliveryLng         도착 경도
 * @param itemDescription     물품 설명 (라이더가 보는 이름)
 * @param deliveryFee         라이더 보상 (snapshot)
 * @param requesterId         요청자 = buyer 의 user_id (라이더 fee 차감 X — escrow 가 hold 중)
 */
public record EscrowConfirmedEvent(
        Long escrowApplicationId,
        String pickupAddress,
        double pickupLat,
        double pickupLng,
        String deliveryAddress,
        double deliveryLat,
        double deliveryLng,
        String itemDescription,
        long deliveryFee,
        Long requesterId
) {
}
