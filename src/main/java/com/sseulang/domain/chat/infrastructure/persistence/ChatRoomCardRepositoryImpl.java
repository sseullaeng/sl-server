package com.sseulang.domain.chat.infrastructure.persistence;

import com.sseulang.domain.chat.domain.ChatRoomCard;
import com.sseulang.domain.chat.domain.ChatRoomCardRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
class ChatRoomCardRepositoryImpl implements ChatRoomCardRepository {

    private final ChatRoomCardMongoRepository mongo;

    ChatRoomCardRepositoryImpl(ChatRoomCardMongoRepository mongo) {
        this.mongo = mongo;
    }

    @Override
    public Optional<ChatRoomCard> findByChatRoomId(Long chatRoomId) {
        return mongo.findByChatRoomId(chatRoomId);
    }

    @Override
    public List<ChatRoomCard> findByChatRoomIdIn(Collection<Long> chatRoomIds) {
        if (chatRoomIds == null || chatRoomIds.isEmpty()) return List.of();
        return mongo.findByChatRoomIdIn(chatRoomIds);
    }

    @Override
    public ChatRoomCard save(ChatRoomCard card) {
        return mongo.save(card);
    }
}
