package com.sseulang.domain.message.presentation.dto;

import com.sseulang.domain.message.application.dto.MessageResult;

import java.time.Instant;
import java.util.List;

public record MessageResponse(
        String id,
        Long chatRoomId,
        Long senderId,
        String content,
        List<String> imageUrls,
        Instant createdAt
) {
    public static MessageResponse from(MessageResult r) {
        return new MessageResponse(
                r.id(), r.chatRoomId(), r.senderId(),
                r.content(), r.imageUrls(), r.createdAt()
        );
    }
}
