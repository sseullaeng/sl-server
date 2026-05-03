package com.sseulang.domain.chat.application.dto;

import com.sseulang.domain.chat.domain.ChatRoom;

import java.time.LocalDateTime;

/**
 * 채팅방 응답 — viewer 기준으로 자동 enrich. ApplicationService 가 batch fetch 로 채움.
 *
 * <p>raw 필드 (user1Id/user2Id/user1Unread/user2Unread) 는 호환을 위해 유지하되, 프론트는
 * viewer 기준 derived 필드 ({@code opponentId} / {@code myUnread} / {@code opponent*})만 봐도 됨.
 * raw 필드는 향후 v2 API 에서 제거 예정.</p>
 */
public record ChatRoomResult(
        Long id,
        Long itemId,
        // raw 필드 (정규화 — user1Id < user2Id) — 호환용
        Long user1Id,
        Long user2Id,
        int user1Unread,
        int user2Unread,
        // viewer-aware enrich
        Long opponentId,
        String opponentNickname,
        String opponentProfileImage,
        int myUnread,
        String itemTitle,
        String itemThumbnailUrl,
        boolean isSeller,                 // viewer 가 이 채팅방의 아이템 판매자인지
        // 메타
        String lastMessage,
        LocalDateTime lastMessageAt,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /**
     * viewerId 기준 derive. opponent / item join 정보는 호출자가 fetch 후 전달 (없으면 null/빈 문자열 OK).
     * viewerId 가 참여자 아니면 IAE — 서비스가 사전 권한 검증 책임.
     */
    public static ChatRoomResult from(
            ChatRoom c,
            Long viewerId,
            String opponentNickname,
            String opponentProfileImage,
            String itemTitle,
            String itemThumbnailUrl,
            Long itemSellerId
    ) {
        Long opponentId;
        int myUnread;
        if (viewerId != null && viewerId.equals(c.getUser1Id())) {
            opponentId = c.getUser2Id();
            myUnread = c.getUser1Unread();
        } else if (viewerId != null && viewerId.equals(c.getUser2Id())) {
            opponentId = c.getUser1Id();
            myUnread = c.getUser2Unread();
        } else {
            // viewer 가 참여자 아님 — 호출자가 사전 검증 책임. enrich 못 하므로 null/0 으로 둠.
            opponentId = null;
            myUnread = 0;
        }
        boolean isSeller = itemSellerId != null && viewerId != null && itemSellerId.equals(viewerId);
        return new ChatRoomResult(
                c.getId(), c.getItemId(),
                c.getUser1Id(), c.getUser2Id(),
                c.getUser1Unread(), c.getUser2Unread(),
                opponentId, opponentNickname, opponentProfileImage, myUnread,
                itemTitle, itemThumbnailUrl, isSeller,
                c.getLastMessage(), c.getLastMessageAt(),
                c.isActive(),
                c.getCreatedAt(), c.getUpdatedAt()
        );
    }
}
