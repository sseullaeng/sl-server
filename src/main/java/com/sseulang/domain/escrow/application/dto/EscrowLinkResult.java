package com.sseulang.domain.escrow.application.dto;

import com.sseulang.domain.escrow.domain.EscrowLink;
import com.sseulang.domain.escrow.domain.FeePayer;
import com.sseulang.domain.escrow.domain.Fragility;
import com.sseulang.domain.escrow.domain.InitiatorRole;
import com.sseulang.domain.escrow.domain.TradeMode;
import com.sseulang.domain.escrow.domain.Volume;
import com.sseulang.domain.escrow.domain.Weight;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record EscrowLinkResult(
        String linkToken,
        String initiatorNickname,
        InitiatorRole initiatorRole,
        FeePayer feePayer,
        TradeMode tradeMode,
        LocalDateTime expiresAt,
        // 발급자 본인 영역 — 수신자가 자기 영역 채울 때 참고 (role 따라 일부 필드만 채워짐)
        String initiatorPickupAddress,
        BigDecimal initiatorPickupLat,
        BigDecimal initiatorPickupLng,
        Long initiatorItemPrice,
        String initiatorItemDescription,
        Weight initiatorWeight,
        Volume initiatorVolume,
        Fragility initiatorFragility,
        String initiatorDeliveryNotes,
        List<String> initiatorImageUrls,
        String initiatorDeliveryAddress,
        BigDecimal initiatorDeliveryLat,
        BigDecimal initiatorDeliveryLng,
        String initiatorReceiverPhone
) {
    public static EscrowLinkResult from(EscrowLink link, String initiatorNickname, List<String> initiatorImageUrls) {
        return new EscrowLinkResult(
                link.getLinkToken(),
                initiatorNickname,
                link.getInitiatorRole(),
                link.getFeePayer(),
                link.getTradeMode(),
                link.getExpiresAt(),
                link.getInitiatorPickupAddress(),
                link.getInitiatorPickupLat(),
                link.getInitiatorPickupLng(),
                link.getInitiatorItemPrice(),
                link.getInitiatorItemDescription(),
                link.getInitiatorWeight(),
                link.getInitiatorVolume(),
                link.getInitiatorFragility(),
                link.getInitiatorDeliveryNotes(),
                initiatorImageUrls,
                link.getInitiatorDeliveryAddress(),
                link.getInitiatorDeliveryLat(),
                link.getInitiatorDeliveryLng(),
                link.getInitiatorReceiverPhone()
        );
    }
}
