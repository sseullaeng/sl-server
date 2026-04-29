package com.sseulang.domain.chat.presentation.dto;

import com.sseulang.domain.chat.application.dto.ChatRoomResult;

import java.time.LocalDateTime;

public record ChatRoomResponse(
        Long id,
        Long itemId,
        Long user1Id,
        Long user2Id,
        String lastMessage,
        LocalDateTime lastMessageAt,
        int user1Unread,
        int user2Unread,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ChatRoomResponse from(ChatRoomResult r) {
        return new ChatRoomResponse(
                r.id(), r.itemId(),
                r.user1Id(), r.user2Id(),
                r.lastMessage(), r.lastMessageAt(),
                r.user1Unread(), r.user2Unread(),
                r.active(),
                r.createdAt(), r.updatedAt()
        );
    }
}
