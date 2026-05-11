package com.sseulang.domain.escrow.application.dto;

import com.sseulang.domain.escrow.domain.FeePayer;
import com.sseulang.domain.escrow.domain.Fragility;
import com.sseulang.domain.escrow.domain.TradeMode;
import com.sseulang.domain.escrow.domain.Volume;
import com.sseulang.domain.escrow.domain.Weight;

import java.math.BigDecimal;
import java.util.List;

/**
 * 내부 draft 신청 (PR-B-4) — 판매자가 본인 영역만 입력하여 정보입력대기 상태 생성.
 * 구매자 영역 (delivery_*, receiver_phone) 은 buyer-info PATCH 로 별도 입력.
 *
 * <p>fee 산정은 양쪽 입력 완료 후 transitionToReadyForPayment 시점.</p>
 */
public record EscrowApplicationCreateInternalDraftCommand(
        Long requesterId,
        Long chatRoomId,
        Long itemId,
        TradeMode tradeMode,
        FeePayer feePayer,
        long itemPrice,
        String itemDescription,
        String pickupAddress,
        BigDecimal pickupLat,
        BigDecimal pickupLng,
        Weight weight,
        Volume volume,
        Fragility fragility,
        String deliveryNotes,
        List<String> imageUrls
) {
}
