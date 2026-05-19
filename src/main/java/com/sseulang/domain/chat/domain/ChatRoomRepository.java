package com.sseulang.domain.chat.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ChatRoomRepository {

    Optional<ChatRoom> findById(Long id);

    

    Optional<ChatRoom> findByItemAndUsers(Long itemId, Long userA, Long userB,
                                          com.sseulang.domain.item.domain.TradeType tradeMode);

    
    Page<ChatRoom> findMine(Long userId, Pageable pageable);

    ChatRoom save(ChatRoom chatRoom);

    

    int recordIncomingMessage(Long chatRoomId, Long senderId, String preview);

    

    int markAsRead(Long chatRoomId, Long userId);

    

    int markAsLeft(Long chatRoomId, Long userId);
}
