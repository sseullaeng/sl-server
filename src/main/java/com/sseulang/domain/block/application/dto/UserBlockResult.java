package com.sseulang.domain.block.application.dto;

import com.sseulang.domain.block.domain.UserBlock;

import java.time.LocalDateTime;

public record UserBlockResult(Long id, Long blockerId, Long blockedId, LocalDateTime createdAt) {
    public static UserBlockResult from(UserBlock b) {
        return new UserBlockResult(b.getId(), b.getBlockerId(), b.getBlockedId(), b.getCreatedAt());
    }
}
