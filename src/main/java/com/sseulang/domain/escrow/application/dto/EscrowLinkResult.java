package com.sseulang.domain.escrow.application.dto;

import com.sseulang.domain.escrow.domain.EscrowLink;
import com.sseulang.domain.escrow.domain.EscrowLinkStatus;
import com.sseulang.domain.escrow.domain.FeePayer;
import com.sseulang.domain.escrow.domain.InitiatorRole;
import com.sseulang.domain.escrow.domain.TradeMode;

import java.time.LocalDateTime;

public record EscrowLinkResult(
        Long id,
        String linkToken,
        Long initiatorId,
        String initiatorNickname,           // public GET 시 노출 — 비로그인 OK
        InitiatorRole initiatorRole,
        FeePayer feePayer,
        TradeMode tradeMode,
        EscrowLinkStatus status,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
) {
    public static EscrowLinkResult from(EscrowLink link, String initiatorNickname) {
        return new EscrowLinkResult(
                link.getId(),
                link.getLinkToken(),
                link.getInitiatorId(),
                initiatorNickname,
                link.getInitiatorRole(),
                link.getFeePayer(),
                link.getTradeMode(),
                link.getStatus(),
                link.getExpiresAt(),
                link.getCreatedAt()
        );
    }
}
