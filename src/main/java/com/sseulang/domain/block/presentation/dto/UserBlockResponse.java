package com.sseulang.domain.block.presentation.dto;

import com.sseulang.domain.block.application.dto.UserBlockResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "사용자 차단 관계 — blocker → blocked. 차단 후 채팅/거래 불가.")
public record UserBlockResponse(
        @Schema(example = "23") Long id,
        @Schema(example = "100", description = "차단한 사용자") Long blockerId,
        @Schema(example = "200", description = "차단당한 사용자") Long blockedId,
        LocalDateTime createdAt
) {
    public static UserBlockResponse from(UserBlockResult r) {
        return new UserBlockResponse(r.id(), r.blockerId(), r.blockedId(), r.createdAt());
    }
}
