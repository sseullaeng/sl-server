package com.sseulang.domain.chat.application.dto;

import com.sseulang.domain.chat.domain.ChatRoom;

import java.time.LocalDateTime;

public record ChatRoomResult(
        Long id,
        Long itemId,
        
        Long user1Id,
        Long user2Id,
        int user1Unread,
        int user2Unread,
        
        Long opponentId,
        String opponentNickname,
        String opponentProfileImage,
        int myUnread,
        String itemTitle,
        String itemThumbnailUrl,
        boolean isSeller,                 
        boolean iLeft,                    
        boolean opponentLeft,             
        
        String lastMessage,
        LocalDateTime lastMessageAt,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        
        SystemCard systemCard
) {
    

    public record SystemCard(
            com.sseulang.domain.item.domain.TradeType tradeMode,
            Long itemId,
            String itemTitle,
            String thumbnailUrl,
            Long price
    ) {
        public static SystemCard from(com.sseulang.domain.chat.domain.ChatRoomCard c) {
            return new SystemCard(c.getTradeMode(), c.getItemId(), c.getItemTitle(), c.getThumbnailUrl(), c.getPrice());
        }
    }
    

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
            
            opponentId = null;
            myUnread = 0;
        }
        return from(c, viewerId, opponentNickname, opponentProfileImage, itemTitle, itemThumbnailUrl, itemSellerId, null);
    }

    

    public static ChatRoomResult from(
            ChatRoom c,
            Long viewerId,
            String opponentNickname,
            String opponentProfileImage,
            String itemTitle,
            String itemThumbnailUrl,
            Long itemSellerId,
            SystemCard systemCard
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
            opponentId = null;
            myUnread = 0;
        }
        boolean isSeller = itemSellerId != null && viewerId != null && itemSellerId.equals(viewerId);
        boolean iLeft = c.iLeft(viewerId);
        boolean opponentLeft = c.opponentLeft(viewerId);
        return new ChatRoomResult(
                c.getId(), c.getItemId(),
                c.getUser1Id(), c.getUser2Id(),
                c.getUser1Unread(), c.getUser2Unread(),
                opponentId, opponentNickname, opponentProfileImage, myUnread,
                itemTitle, itemThumbnailUrl, isSeller,
                iLeft, opponentLeft,
                c.getLastMessage(), c.getLastMessageAt(),
                c.isActive(),
                c.getCreatedAt(), c.getUpdatedAt(),
                systemCard
        );
    }
}
