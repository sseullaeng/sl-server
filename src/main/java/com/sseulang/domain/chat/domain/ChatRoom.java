package com.sseulang.domain.chat.domain;

import com.sseulang.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "user1_id", nullable = false)
    private Long user1Id;

    @Column(name = "user2_id", nullable = false)
    private Long user2Id;

    @Column(name = "last_message", length = 500)
    private String lastMessage;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Column(name = "user1_unread", nullable = false)
    private int user1Unread;

    @Column(name = "user2_unread", nullable = false)
    private int user2Unread;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "user1_left_at")
    private LocalDateTime user1LeftAt;

    @Column(name = "user2_left_at")
    private LocalDateTime user2LeftAt;

    

    @Enumerated(EnumType.STRING)
    @Column(name = "trade_mode", nullable = false, length = 10)
    private com.sseulang.domain.item.domain.TradeType tradeMode;

    

    public static ChatRoom openFor(Long itemId, Long requesterId, Long opponentId,
                                   com.sseulang.domain.item.domain.TradeType tradeMode) {
        if (itemId == null || itemId <= 0) {
            throw new IllegalArgumentException("itemId 는 양수여야 합니다");
        }
        if (requesterId == null || requesterId <= 0) {
            throw new IllegalArgumentException("requesterId 는 양수여야 합니다");
        }
        if (opponentId == null || opponentId <= 0) {
            throw new IllegalArgumentException("opponentId 는 양수여야 합니다");
        }
        if (requesterId.equals(opponentId)) {
            throw new IllegalArgumentException("requester 와 opponent 는 같을 수 없습니다");
        }
        if (tradeMode == null) {
            throw new IllegalArgumentException("tradeMode 는 필수입니다");
        }

        ChatRoom cr = new ChatRoom();
        cr.itemId = itemId;
        cr.user1Id = Math.min(requesterId, opponentId);
        cr.user2Id = Math.max(requesterId, opponentId);
        cr.user1Unread = 0;
        cr.user2Unread = 0;
        cr.active = true;
        cr.tradeMode = tradeMode;
        return cr;
    }

    
    @Deprecated
    public static ChatRoom openFor(Long itemId, Long requesterId, Long opponentId) {
        return openFor(itemId, requesterId, opponentId, com.sseulang.domain.item.domain.TradeType.판매);
    }

    public boolean isParticipant(Long userId) {
        return userId != null && (userId.equals(user1Id) || userId.equals(user2Id));
    }

    

    public void markAsRead(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 는 필수입니다");
        }
        if (userId.equals(user1Id)) {
            this.user1Unread = 0;
        } else if (userId.equals(user2Id)) {
            this.user2Unread = 0;
        }
    }

    

    public void leave(Long userId, LocalDateTime now) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 는 필수입니다");
        }
        if (now == null) {
            throw new IllegalArgumentException("now 는 필수입니다");
        }
        if (userId.equals(user1Id)) {
            if (this.user1LeftAt == null) {
                this.user1LeftAt = now;
            }
        } else if (userId.equals(user2Id)) {
            if (this.user2LeftAt == null) {
                this.user2LeftAt = now;
            }
        } else {
            throw new IllegalStateException("채팅방 참여자만 나갈 수 있습니다 — userId=" + userId);
        }
    }

    

    public boolean iLeft(Long userId) {
        if (userId == null) return false;
        if (userId.equals(user1Id)) return this.user1LeftAt != null;
        if (userId.equals(user2Id)) return this.user2LeftAt != null;
        return false;
    }

    

    public boolean opponentLeft(Long userId) {
        if (userId == null) return false;
        if (userId.equals(user1Id)) return this.user2LeftAt != null;
        if (userId.equals(user2Id)) return this.user1LeftAt != null;
        return false;
    }
}
