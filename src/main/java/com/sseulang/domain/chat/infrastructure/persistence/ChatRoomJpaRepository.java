package com.sseulang.domain.chat.infrastructure.persistence;

import com.sseulang.domain.chat.domain.ChatRoom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
