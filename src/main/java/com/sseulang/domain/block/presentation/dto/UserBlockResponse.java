package com.sseulang.domain.block.presentation.dto;

import com.sseulang.domain.block.application.dto.UserBlockResult;

import java.time.LocalDateTime;

public record UserBlockResponse(Long id, Long blockerId, Long blockedId, LocalDateTime createdAt) {
    public static UserBlockResponse from(UserBlockResult r) {
        return new UserBlockResponse(r.id(), r.blockerId(), r.blockedId(), r.createdAt());
    }
}
