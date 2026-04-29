package com.sseulang.domain.chat.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
