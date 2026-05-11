package com.sseulang.domain.chat.infrastructure.persistence;

import com.sseulang.domain.chat.domain.ChatRoomCard;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

interface ChatRoomCardMongoRepository extends MongoRepository<ChatRoomCard, String> {
    Optional<ChatRoomCard> findByChatRoomId(Long chatRoomId);
}
