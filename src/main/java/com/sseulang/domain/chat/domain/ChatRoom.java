package com.sseulang.domain.chat.domain;

import com.sseulang.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ChatRoom Aggregate Root. V1 스키마 {@code chat_rooms} 매핑 — 메타데이터만 (메시지는 MongoDB, Day 6).
 *
 * <p>가이드 §4.10: 1:1 고정. UNIQUE(item_id, user1_id, user2_id) — 같은 두 사용자 + 같은 물품 은 단일 방.
 * 정규화: 항상 {@code user1Id < user2Id} 강제 → 두 user 쌍이 어떤 순서로 들어와도 동일 행 매칭.</p>
 *
 * <p>last_message / last_message_at / unread 카운트 갱신은 메시지 도메인(Day 6)이 담당.
 * 본 PR 은 채팅방 메타 + 멱등 생성/조회만.</p>
 */
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

    /**
     * {@code (itemId, requesterId, opponentId)} 로 채팅방 생성. {@code user1Id < user2Id} 강제 정규화.
     */
    public static ChatRoom openFor(Long itemId, Long requesterId, Long opponentId) {
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

        ChatRoom cr = new ChatRoom();
        cr.itemId = itemId;
        cr.user1Id = Math.min(requesterId, opponentId);
        cr.user2Id = Math.max(requesterId, opponentId);
        cr.user1Unread = 0;
        cr.user2Unread = 0;
        cr.active = true;
        return cr;
    }

    public boolean isParticipant(Long userId) {
        return userId != null && (userId.equals(user1Id) || userId.equals(user2Id));
    }

    /**
     * 본인이 user1 이면 user1Unread = 0, user2 면 user2Unread = 0. 상대방 unread 는 그대로.
     * 호출자가 사전에 isParticipant 검증해야 함 (서비스 책임).
     */
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

    /**
     * 본인 측 채팅방을 soft hide. 본인이 user1 이면 user1LeftAt = now, user2 면 user2LeftAt = now.
     * 이미 left 상태면 no-op (idempotent). 비참여자 호출은 IllegalStateException — 서비스가 사전 검증해야 함.
     */
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

    /**
     * 본인이 채팅방을 나갔는지. 비참여자는 false (의미 없는 질문이라 false 로 통일).
     */
    public boolean iLeft(Long userId) {
        if (userId == null) return false;
        if (userId.equals(user1Id)) return this.user1LeftAt != null;
        if (userId.equals(user2Id)) return this.user2LeftAt != null;
        return false;
    }

    /**
     * 상대방이 채팅방을 나갔는지. 비참여자는 false (의미 없는 질문이라 false 로 통일).
     */
    public boolean opponentLeft(Long userId) {
        if (userId == null) return false;
        if (userId.equals(user1Id)) return this.user2LeftAt != null;
        if (userId.equals(user2Id)) return this.user1LeftAt != null;
        return false;
    }
}
