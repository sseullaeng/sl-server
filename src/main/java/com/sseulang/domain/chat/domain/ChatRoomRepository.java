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

    /**
     * 메시지 발신 시 last_message + last_message_at + 상대방 unread 를 단일 atomic UPDATE 로 갱신.
     * 가이드 §4.10 — 동시 메시지 발신 race 안전 (마지막 SQL 이 win).
     */
    int recordIncomingMessage(Long chatRoomId, Long senderId, String preview);

    /**
     * 본인 unread 만 0 으로 atomic UPDATE. 비참여자는 영향 0. 권한 검증은 서비스 책임.
     */
    int markAsRead(Long chatRoomId, Long userId);

    /**
     * 본인 측 left_at 을 NOW() 로 atomic UPDATE (soft hide). 이미 left 면 그 값 유지.
     * 비참여자는 0 행 영향 — 서비스가 사전 권한 검증. 이미 left 상태도 1 행 영향 가능 (CASE WHEN 로 보존).
     */
    int markAsLeft(Long chatRoomId, Long userId);
}
