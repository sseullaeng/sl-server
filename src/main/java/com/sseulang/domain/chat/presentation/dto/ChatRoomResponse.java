package com.sseulang.domain.chat.presentation.dto;

import com.sseulang.domain.chat.application.dto.ChatRoomResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "1:1 채팅방 — (user1, user2, item) UNIQUE. user1Id < user2Id 정규화.")
public record ChatRoomResponse(
        @Schema(example = "8") Long id,
        @Schema(example = "42") Long itemId,
        @Schema(description = "정규화된 첫 사용자 (id 작은 쪽)", example = "100") Long user1Id,
        @Schema(description = "정규화된 두번째 사용자", example = "200") Long user2Id,
        @Schema(example = "안녕하세요, 거래 가능할까요?", description = "최근 메시지 미리보기") String lastMessage,
        LocalDateTime lastMessageAt,
        @Schema(example = "0", description = "user1 의 미읽음 카운트") int user1Unread,
        @Schema(example = "3", description = "user2 의 미읽음 카운트") int user2Unread,
        @Schema(description = "false 면 차단/예약 등으로 비활성") boolean active,
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
