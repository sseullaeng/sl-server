package com.sseulang.domain.chat.domain;

import java.util.Optional;

/**
 * 채팅방 시스템 카드 Repository — chatRoomId UNIQUE.
 */
public interface ChatRoomCardRepository {

    Optional<ChatRoomCard> findByChatRoomId(Long chatRoomId);

    ChatRoomCard save(ChatRoomCard card);
}
