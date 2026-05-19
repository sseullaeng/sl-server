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
          AND c.tradeMode = :tradeMode
    """)
    Optional<ChatRoom> findByItemAndUsersNormalized(
            @Param("itemId") Long itemId,
            @Param("u1") Long u1,
            @Param("u2") Long u2,
            @Param("tradeMode") com.sseulang.domain.item.domain.TradeType tradeMode
    );

    

    @Query("""
        SELECT c FROM ChatRoom c
        WHERE (c.user1Id = :userId AND c.user1LeftAt IS NULL)
           OR (c.user2Id = :userId AND c.user2LeftAt IS NULL)
        ORDER BY
          CASE WHEN c.lastMessageAt IS NULL THEN 1 ELSE 0 END,
          c.lastMessageAt DESC,
          c.id DESC
    """)
    Page<ChatRoom> findMine(@Param("userId") Long userId, Pageable pageable);

    

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

    
    @Modifying
    @Query("""
        UPDATE ChatRoom c
        SET c.user1Unread = CASE WHEN c.user1Id = :userId THEN 0 ELSE c.user1Unread END,
            c.user2Unread = CASE WHEN c.user2Id = :userId THEN 0 ELSE c.user2Unread END
        WHERE c.id = :roomId
          AND (c.user1Id = :userId OR c.user2Id = :userId)
    """)
    int markAsRead(@Param("roomId") Long roomId, @Param("userId") Long userId);

    

    @Modifying
    @Query("""
        UPDATE ChatRoom c
        SET c.user1LeftAt = CASE WHEN c.user1Id = :userId AND c.user1LeftAt IS NULL THEN :leftAt ELSE c.user1LeftAt END,
            c.user2LeftAt = CASE WHEN c.user2Id = :userId AND c.user2LeftAt IS NULL THEN :leftAt ELSE c.user2LeftAt END
        WHERE c.id = :roomId
          AND (c.user1Id = :userId OR c.user2Id = :userId)
    """)
    int markAsLeft(
            @Param("roomId") Long roomId,
            @Param("userId") Long userId,
            @Param("leftAt") LocalDateTime leftAt
    );
}
