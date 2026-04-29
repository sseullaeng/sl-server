package com.sseulang.domain.chat.infrastructure.persistence;

import com.sseulang.domain.chat.domain.ChatRoom;
import com.sseulang.domain.chat.domain.ChatRoomRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class ChatRoomRepositoryImpl implements ChatRoomRepository {

    private final ChatRoomJpaRepository jpa;

    public ChatRoomRepositoryImpl(ChatRoomJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<ChatRoom> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<ChatRoom> findByItemAndUsers(Long itemId, Long userA, Long userB) {
        long u1 = Math.min(userA, userB);
        long u2 = Math.max(userA, userB);
        return jpa.findByItemAndUsersNormalized(itemId, u1, u2);
    }

    @Override
    public Page<ChatRoom> findMine(Long userId, Pageable pageable) {
        return jpa.findMine(userId, pageable);
    }

    @Override
    public ChatRoom save(ChatRoom chatRoom) {
        return jpa.save(chatRoom);
    }

    @Override
    public int recordIncomingMessage(Long chatRoomId, Long senderId, String preview) {
        return jpa.recordIncomingMessage(chatRoomId, senderId, preview, java.time.LocalDateTime.now());
    }
}
