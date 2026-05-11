package com.sseulang.domain.chat.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ChatRoomRepository {

    Optional<ChatRoom> findById(Long id);

    /**
     * 정규화된 (user1, user2) 쌍 + itemId + tradeMode 로 기존 방 조회.
     * 라운드 12 PR-C — 같은 사용자/아이템 쌍이라도 거래방식 다르면 별도 방이므로 tradeMode 도 매칭 키.
     */
    Optional<ChatRoom> findByItemAndUsers(Long itemId, Long userA, Long userB,
                                          com.sseulang.domain.item.domain.TradeType tradeMode);

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
