package com.sseulang.domain.escrow.application.dto;

import com.sseulang.domain.escrow.domain.EscrowLink;
import com.sseulang.domain.escrow.domain.FeePayer;
import com.sseulang.domain.escrow.domain.InitiatorRole;
import com.sseulang.domain.escrow.domain.TradeMode;

import java.time.LocalDateTime;

public record EscrowLinkResult(
        String linkToken,
        String initiatorNickname,
        InitiatorRole initiatorRole,
        FeePayer feePayer,
        TradeMode tradeMode,
        LocalDateTime expiresAt
) {
    public static EscrowLinkResult from(EscrowLink link, String initiatorNickname) {
        return new EscrowLinkResult(
                link.getLinkToken(),
                initiatorNickname,
                link.getInitiatorRole(),
                link.getFeePayer(),
                link.getTradeMode(),
                link.getExpiresAt()
        );
    }
}
