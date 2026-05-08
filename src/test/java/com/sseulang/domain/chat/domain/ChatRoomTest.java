package com.sseulang.domain.chat.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatRoomTest {

    @Test
    @DisplayName("openFor 정상_user1<user2 정규화")
    void openFor_정규화() {
        ChatRoom a = ChatRoom.openFor(10L, 200L, 100L);
        assertThat(a.getUser1Id()).isEqualTo(100L);
        assertThat(a.getUser2Id()).isEqualTo(200L);

        // 인자 순서 반대로 와도 동일
        ChatRoom b = ChatRoom.openFor(10L, 100L, 200L);
        assertThat(b.getUser1Id()).isEqualTo(100L);
        assertThat(b.getUser2Id()).isEqualTo(200L);

        assertThat(a.isActive()).isTrue();
        assertThat(a.getUser1Unread()).isZero();
        assertThat(a.getUser2Unread()).isZero();
    }

    @Test
    @DisplayName("openFor 자기 자신_거부")
    void openFor_self_거부() {
        assertThatThrownBy(() -> ChatRoom.openFor(10L, 100L, 100L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("openFor null/비양수_거부")
    void openFor_invalid_거부() {
        assertThatThrownBy(() -> ChatRoom.openFor(null, 1L, 2L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChatRoom.openFor(0L, 1L, 2L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChatRoom.openFor(1L, null, 2L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChatRoom.openFor(1L, 1L, -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("isParticipant")
    void isParticipant() {
        ChatRoom c = ChatRoom.openFor(10L, 100L, 200L);
        assertThat(c.isParticipant(100L)).isTrue();
        assertThat(c.isParticipant(200L)).isTrue();
        assertThat(c.isParticipant(300L)).isFalse();
        assertThat(c.isParticipant(null)).isFalse();
    }

    @Test
    @DisplayName("leave 본인_user1_left_at 설정")
    void leave_user1() {
        ChatRoom c = ChatRoom.openFor(10L, 100L, 200L);
        LocalDateTime now = LocalDateTime.of(2026, 5, 8, 12, 0);

        c.leave(100L, now);

        assertThat(c.getUser1LeftAt()).isEqualTo(now);
        assertThat(c.getUser2LeftAt()).isNull();
        assertThat(c.iLeft(100L)).isTrue();
        assertThat(c.iLeft(200L)).isFalse();
        assertThat(c.opponentLeft(100L)).isFalse();
        assertThat(c.opponentLeft(200L)).isTrue();
    }

    @Test
    @DisplayName("leave 본인_user2_left_at 설정")
    void leave_user2() {
        ChatRoom c = ChatRoom.openFor(10L, 100L, 200L);
        LocalDateTime now = LocalDateTime.of(2026, 5, 8, 12, 0);

        c.leave(200L, now);

        assertThat(c.getUser1LeftAt()).isNull();
        assertThat(c.getUser2LeftAt()).isEqualTo(now);
        assertThat(c.iLeft(200L)).isTrue();
        assertThat(c.iLeft(100L)).isFalse();
        assertThat(c.opponentLeft(200L)).isFalse();
        assertThat(c.opponentLeft(100L)).isTrue();
    }

    @Test
    @DisplayName("leave 이미_left_상태_idempotent")
    void leave_idempotent() {
        ChatRoom c = ChatRoom.openFor(10L, 100L, 200L);
        LocalDateTime first = LocalDateTime.of(2026, 5, 8, 12, 0);
        LocalDateTime second = LocalDateTime.of(2026, 5, 8, 13, 0);

        c.leave(100L, first);
        c.leave(100L, second);  // 다시 호출해도 첫 시각 유지

        assertThat(c.getUser1LeftAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("leave 비참여자_IllegalStateException")
    void leave_비참여자_거부() {
        ChatRoom c = ChatRoom.openFor(10L, 100L, 200L);
        LocalDateTime now = LocalDateTime.of(2026, 5, 8, 12, 0);

        assertThatThrownBy(() -> c.leave(300L, now))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("leave null_userId/now_거부")
    void leave_null_거부() {
        ChatRoom c = ChatRoom.openFor(10L, 100L, 200L);
        LocalDateTime now = LocalDateTime.of(2026, 5, 8, 12, 0);

        assertThatThrownBy(() -> c.leave(null, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> c.leave(100L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("iLeft / opponentLeft — 비참여자는 false")
    void iLeft_비참여자_false() {
        ChatRoom c = ChatRoom.openFor(10L, 100L, 200L);
        c.leave(100L, LocalDateTime.now());

        assertThat(c.iLeft(300L)).isFalse();
        assertThat(c.iLeft(null)).isFalse();
        assertThat(c.opponentLeft(300L)).isFalse();
        assertThat(c.opponentLeft(null)).isFalse();
    }
}
