package com.sseulang.domain.chat.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ChatRoomRepository {

    Optional<ChatRoom> findById(Long id);

    /**
     * 정규화된 (user1, user2) 쌍과 itemId 로 기존 방 조회. 인자는 정규화 전이어도 메서드가 정렬해 lookup.
     */
    Optional<ChatRoom> findByItemAndUsers(Long itemId, Long userA, Long userB);

    /** 내가 참여자인 채팅방 목록 — last_message_at desc (null 은 후순위). */
    Page<ChatRoom> findMine(Long userId, Pageable pageable);

    ChatRoom save(ChatRoom chatRoom);
}
