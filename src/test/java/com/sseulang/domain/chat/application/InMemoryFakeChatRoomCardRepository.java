package com.sseulang.domain.chat.application;

import com.sseulang.domain.chat.domain.ChatRoomCard;
import com.sseulang.domain.chat.domain.ChatRoomCardRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryFakeChatRoomCardRepository implements ChatRoomCardRepository {

    private final Map<Long, ChatRoomCard> store = new HashMap<>();

    @Override
    public Optional<ChatRoomCard> findByChatRoomId(Long chatRoomId) {
        return Optional.ofNullable(store.get(chatRoomId));
    }

    @Override
    public ChatRoomCard save(ChatRoomCard card) {
        store.put(card.getChatRoomId(), card);
        return card;
    }
}
