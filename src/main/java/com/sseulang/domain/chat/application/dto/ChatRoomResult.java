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
        
        SystemCard card
) {


    public record SystemCard(
            // 카드 종류 — Item(거래 없음) / Transaction / EscrowApplication 중 활성 거래 우선
            String cardKind,
            com.sseulang.domain.item.domain.TradeType tradeMode,
            Long itemId,
            String itemTitle,
            String itemThumbnailUrl,
            Long price,
            // 활성 직거래 — 없으면 null
            Long transactionId,
            String transactionStatus,
            // 활성 거래대행 — 없으면 null. INTERNAL 거래대행만.
            Long escrowApplicationId,
            String escrowStatus,
            Long deliveryId,
            // V43 — 대여 거래/거래대행 한정. 비대여 또는 기간 미입력은 null.
            LocalDateTime rentalStart,
            LocalDateTime rentalEnd
    ) {
        public static SystemCard from(com.sseulang.domain.chat.domain.ChatRoomCard c) {
            return new SystemCard("Item", c.getTradeMode(), c.getItemId(), c.getItemTitle(),
                    c.getThumbnailUrl(), c.getPrice(),
                    null, null, null, null, null,
                    null, null);
        }

        public SystemCard withTransaction(Long txId, String status, LocalDateTime rentalStart, LocalDateTime rentalEnd) {
            return new SystemCard("Transaction", tradeMode, itemId, itemTitle, itemThumbnailUrl, price,
                    txId, status, null, null, null,
                    rentalStart, rentalEnd);
        }

        public SystemCard withEscrow(Long escrowId, String status, Long deliveryId, Long pairedTransactionId,
                                     LocalDateTime rentalStart, LocalDateTime rentalEnd) {
            return new SystemCard("EscrowApplication", tradeMode, itemId, itemTitle, itemThumbnailUrl, price,
                    pairedTransactionId, status, escrowId, status, deliveryId,
                    rentalStart, rentalEnd);
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
            SystemCard card
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
                card
        );
    }
}
