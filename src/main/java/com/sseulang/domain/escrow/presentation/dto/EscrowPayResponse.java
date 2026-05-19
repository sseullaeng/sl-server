package com.sseulang.domain.escrow.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "거래대행 본인 share 결제 응답 — 후속 상태 노출.")
public record EscrowPayResponse(
        @Schema(example = "결제완료", description = "결제대기 | 결제완료 | 진행중") String status
) { }
