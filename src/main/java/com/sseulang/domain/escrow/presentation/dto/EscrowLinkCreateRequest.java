package com.sseulang.domain.escrow.presentation.dto;

import com.sseulang.domain.escrow.application.dto.EscrowLinkCreateCommand;
import com.sseulang.domain.escrow.domain.FeePayer;
import com.sseulang.domain.escrow.domain.InitiatorRole;
import com.sseulang.domain.escrow.domain.TradeMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "거래대행 link 생성 (신청자). role + feePayer + tradeMode 결정.")
public record EscrowLinkCreateRequest(
        @Schema(description = "신청자 역할 (수신자는 반대)", example = "buyer", allowableValues = {"buyer", "seller"})
        @NotNull InitiatorRole role,

        @Schema(description = "수수료 부담", example = "buyer", allowableValues = {"buyer", "seller", "both"})
        @NotNull FeePayer feePayer,

        @Schema(description = "거래 모드 — INTERNAL (쓸랭 내) / EXTERNAL (외부 거래 + 배달만)",
                example = "INTERNAL", allowableValues = {"INTERNAL", "EXTERNAL"})
        @NotNull TradeMode tradeMode
) {
    public EscrowLinkCreateCommand toCommand(Long initiatorId) {
        return new EscrowLinkCreateCommand(initiatorId, role, feePayer, tradeMode);
    }
}
