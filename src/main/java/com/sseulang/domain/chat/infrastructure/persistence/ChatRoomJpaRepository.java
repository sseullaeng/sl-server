package com.sseulang.domain.chat.infrastructure.persistence;

import com.sseulang.domain.chat.domain.ChatRoom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

interface ChatRoomJpaRepository extends JpaRepository<ChatRoom, Long> {

    @Query("""
        SELECT c FROM ChatRoom c
        WHERE c.itemId = :itemId
          AND c.user1Id = :u1
          AND c.user2Id = :u2
    """)
    Optional<ChatRoom> findByItemAndUsersNormalized(
            @Param("itemId") Long itemId,
            @Param("u1") Long u1,
            @Param("u2") Long u2
    );

    @Query("""
        SELECT c FROM ChatRoom c
        WHERE c.user1Id = :userId OR c.user2Id = :userId
        ORDER BY
          CASE WHEN c.lastMessageAt IS NULL THEN 1 ELSE 0 END,
          c.lastMessageAt DESC,
          c.id DESC
    """)
    Page<ChatRoom> findMine(@Param("userId") Long userId, Pageable pageable);

    /**
     * 단일 atomic UPDATE — last_message / last_message_at / 상대방 unread 갱신.
     * 발신자 본인 unread 는 0 으로 (자기가 보낸 메시지는 안 읽음 카운트 X).
     */
    @Modifying
    @Query("""
        UPDATE ChatRoom c
        SET c.lastMessage = :preview,
            c.lastMessageAt = :sentAt,
            c.user1Unread = CASE WHEN c.user1Id = :senderId THEN c.user1Unread ELSE c.user1Unread + 1 END,
            c.user2Unread = CASE WHEN c.user2Id = :senderId THEN c.user2Unread ELSE c.user2Unread + 1 END
        WHERE c.id = :roomId
    """)
    int recordIncomingMessage(
            @Param("roomId") Long roomId,
            @Param("senderId") Long senderId,
            @Param("preview") String preview,
            @Param("sentAt") LocalDateTime sentAt
    );

    /** 본인 unread 0 으로 atomic UPDATE. 비참여자는 영향 0. */
    @Modifying
    @Query("""
        UPDATE ChatRoom c
        SET c.user1Unread = CASE WHEN c.user1Id = :userId THEN 0 ELSE c.user1Unread END,
            c.user2Unread = CASE WHEN c.user2Id = :userId THEN 0 ELSE c.user2Unread END
        WHERE c.id = :roomId
          AND (c.user1Id = :userId OR c.user2Id = :userId)
    """)
    int markAsRead(@Param("roomId") Long roomId, @Param("userId") Long userId);
}
