package com.sseulang.domain.escrow.application.dto;

import com.sseulang.domain.escrow.domain.Fragility;
import com.sseulang.domain.escrow.domain.Volume;
import com.sseulang.domain.escrow.domain.Weight;

import java.math.BigDecimal;
import java.util.List;

// 라운드 12 분리 입력 — 수신자가 본인 영역만 채워서 link 매칭 → application 생성.
// role 따라 수신자가 채우는 영역이 달라지므로 모두 nullable. 서비스에서 link.role 보고 검증.
public record EscrowApplicationByLinkCommand(
        Long receiverId,
        String linkToken,
        // seller 가 발급한 link → 수신자(buyer) 가 delivery + receiverPhone 입력
        String deliveryAddress,
        BigDecimal deliveryLat,
        BigDecimal deliveryLng,
        String receiverPhone,
        // buyer 가 발급한 link → 수신자(seller) 가 pickup + 물품 정보 입력
        String pickupAddress,
        BigDecimal pickupLat,
        BigDecimal pickupLng,
        Long itemPrice,
        String itemDescription,
        Weight weight,
        Volume volume,
        Fragility fragility,
        String deliveryNotes,
        List<String> imageUrls,
        // preview 단계 fee snapshot — ±10원 검증
        long submittedDeliveryFee,
        long submittedCommissionFee,
        long submittedTotalFee,
        BigDecimal submittedDistanceKm
) {
}
