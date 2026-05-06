package com.sseulang.domain.escrow.application.dto;

import com.sseulang.domain.escrow.domain.FeePayer;
import com.sseulang.domain.escrow.domain.InitiatorRole;
import com.sseulang.domain.escrow.domain.TradeMode;

public record EscrowLinkCreateCommand(
        Long initiatorId,
        InitiatorRole initiatorRole,
        FeePayer feePayer,
        TradeMode tradeMode
) {
}
