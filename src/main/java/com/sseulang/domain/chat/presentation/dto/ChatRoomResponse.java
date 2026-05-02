package com.sseulang.domain.chat.presentation.dto;

import com.sseulang.domain.chat.application.dto.ChatRoomResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "1:1 채팅방. viewer 기준 enrich — opponent* / myUnread / item* 자동 join. raw user1/user2 필드는 호환용 유지.")
public record ChatRoomResponse(
        @Schema(example = "8") Long id,
        @Schema(example = "42") Long itemId,
        // viewer 기준 derived 필드 — 프론트는 이것만 보면 됨
        @Schema(example = "200", description = "viewer 의 상대방 사용자 id") Long opponentId,
        @Schema(example = "쓸랭이", description = "상대방 닉네임") String opponentNickname,
        @Schema(description = "상대방 프로필 이미지 URL (없으면 null)") String opponentProfileImage,
        @Schema(example = "3", description = "viewer 의 미읽음 카운트") int myUnread,
        @Schema(example = "아이폰 14 Pro", description = "아이템 제목") String itemTitle,
        @Schema(description = "아이템 썸네일 URL (없으면 null)") String itemThumbnailUrl,
        // 메타
        @Schema(example = "안녕하세요...", description = "최근 메시지 미리보기") String lastMessage,
        LocalDateTime lastMessageAt,
        @Schema(description = "false 면 차단/예약 등으로 비활성") boolean active,
        // raw — 호환용 (향후 제거 예정)
        @Schema(description = "정규화 raw — user1Id < user2Id. 호환용 유지, 신규 코드는 opponentId 사용 권장") Long user1Id,
        @Schema(description = "정규화 raw — 호환용 유지") Long user2Id,
        @Schema(description = "raw — user1 미읽음. 호환용 유지, 신규 코드는 myUnread 사용 권장") int user1Unread,
        @Schema(description = "raw — user2 미읽음. 호환용 유지") int user2Unread,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ChatRoomResponse from(ChatRoomResult r) {
        return new ChatRoomResponse(
                r.id(), r.itemId(),
                r.opponentId(), r.opponentNickname(), r.opponentProfileImage(),
                r.myUnread(),
                r.itemTitle(), r.itemThumbnailUrl(),
                r.lastMessage(), r.lastMessageAt(), r.active(),
                r.user1Id(), r.user2Id(), r.user1Unread(), r.user2Unread(),
                r.createdAt(), r.updatedAt()
        );
    }
}
