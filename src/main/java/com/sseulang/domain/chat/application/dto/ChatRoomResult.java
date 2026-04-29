package com.sseulang.domain.chat.application.dto;

import com.sseulang.domain.chat.domain.ChatRoom;

import java.time.LocalDateTime;

public record ChatRoomResult(
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
    public static ChatRoomResult from(ChatRoom c) {
        return new ChatRoomResult(
                c.getId(), c.getItemId(),
                c.getUser1Id(), c.getUser2Id(),
                c.getLastMessage(), c.getLastMessageAt(),
                c.getUser1Unread(), c.getUser2Unread(),
                c.isActive(),
                c.getCreatedAt(), c.getUpdatedAt()
        );
    }
}
