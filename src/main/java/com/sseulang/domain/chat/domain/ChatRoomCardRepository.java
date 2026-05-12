package com.sseulang.domain.chat.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChatRoomCardRepository {

    Optional<ChatRoomCard> findByChatRoomId(Long chatRoomId);

    List<ChatRoomCard> findByChatRoomIdIn(Collection<Long> chatRoomIds);

    ChatRoomCard save(ChatRoomCard card);
}
