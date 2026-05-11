package com.sseulang.domain.chat.domain;

import java.util.Optional;

public interface ChatRoomCardRepository {

    Optional<ChatRoomCard> findByChatRoomId(Long chatRoomId);

    ChatRoomCard save(ChatRoomCard card);
}
