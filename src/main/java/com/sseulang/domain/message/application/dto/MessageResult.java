package com.sseulang.domain.message.application.dto;

import com.sseulang.domain.message.domain.Message;

import java.time.Instant;
import java.util.List;

public record MessageResult(
        String id,
        Long chatRoomId,
        Long senderId,
        String content,
        List<String> imageUrls,
        Instant createdAt
) {
    public static MessageResult from(Message m) {
        return new MessageResult(
                m.getId(), m.getChatRoomId(), m.getSenderId(),
                m.getContent(), m.getImageUrls(), m.getCreatedAt()
        );
    }
}
