package com.sseulang.domain.escrow.application.dto;

import java.math.BigDecimal;

/**
 * 구매자 영역 입력 (PR-B-4) — 정보입력대기 상태에서만.
 * 수령지 + 수령자 연락처. 양쪽 filled 시 ApplicationService 가 fee 산정 + 결제대기 전환.
 */
public record EscrowBuyerInfoPatchCommand(
        String deliveryAddress,
        BigDecimal deliveryLat,
        BigDecimal deliveryLng,
        String receiverPhone
) {
}
