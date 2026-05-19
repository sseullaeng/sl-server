package com.sseulang.domain.chat.application;

import com.sseulang.domain.chat.domain.ChatRoomCard;
import com.sseulang.domain.chat.domain.ChatRoomCardRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class NoOpChatRoomCardRepository implements ChatRoomCardRepository {

    @Override
    public Optional<ChatRoomCard> findByChatRoomId(Long chatRoomId) {
        return Optional.empty();
    }

    @Override
    public List<ChatRoomCard> findByChatRoomIdIn(Collection<Long> chatRoomIds) {
        return List.of();
    }

    @Override
    public ChatRoomCard save(ChatRoomCard card) {
        return card;
    }
}
