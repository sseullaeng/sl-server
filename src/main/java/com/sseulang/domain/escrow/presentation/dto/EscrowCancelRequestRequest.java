package com.sseulang.domain.escrow.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "사용중 단계 취소 요청 — 양 당사자 합의 취소 (라운드 14 PR7).")
public record EscrowCancelRequestRequest(
        @Schema(description = "취소 사유 (선택)", example = "사용 중 사정이 생겨서", maxLength = 500, nullable = true)
        @Size(max = 500) String reason
) {
}
