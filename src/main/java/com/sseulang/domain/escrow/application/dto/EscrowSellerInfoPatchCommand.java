package com.sseulang.domain.escrow.application.dto;

import com.sseulang.domain.escrow.domain.Fragility;
import com.sseulang.domain.escrow.domain.Volume;
import com.sseulang.domain.escrow.domain.Weight;

import java.math.BigDecimal;

/**
 * 판매자 영역 수정 (PR-B-4) — 정보입력대기 상태에서만.
 * 출발지 / 물품 정보 / 가격을 수정 가능. delivery 좌표는 영향 없음.
 */
public record EscrowSellerInfoPatchCommand(
        String pickupAddress,
        BigDecimal pickupLat,
        BigDecimal pickupLng,
        Weight weight,
        Volume volume,
        Fragility fragility,
        long itemPrice,
        String itemDescription,
        String deliveryNotes
) {
}
