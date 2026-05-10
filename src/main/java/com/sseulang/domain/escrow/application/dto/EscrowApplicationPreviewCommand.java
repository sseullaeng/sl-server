package com.sseulang.domain.escrow.application.dto;

import com.sseulang.domain.escrow.domain.FeePayer;
import com.sseulang.domain.escrow.domain.Fragility;
import com.sseulang.domain.escrow.domain.TradeMode;
import com.sseulang.domain.escrow.domain.Volume;
import com.sseulang.domain.escrow.domain.Weight;

import java.math.BigDecimal;

/**
 * 거래대행 수수료 미리보기 Command — 신청 폼 작성 중 실시간 호출용.
 * 백엔드가 좌표로 거리 계산 + 정책 적용 + share 산정해서 응답.
 *
 * <p>actually persisting 아무것도 안 함 — pure read.</p>
 */
public record EscrowApplicationPreviewCommand(
        TradeMode tradeMode,
        long itemPrice,
        BigDecimal pickupLat,
        BigDecimal pickupLng,
        BigDecimal deliveryLat,
        BigDecimal deliveryLng,
        Weight weight,
        Volume volume,
        Fragility fragility,
        FeePayer feePayer
) {
}
