package com.sseulang.domain.notification.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTest {

    @Test
    @DisplayName("create 정상_read=false")
    void create_정상() {
        Notification n = Notification.create(
                1L, NotificationType.메시지, "새 메시지", "안녕", "chat-room", 10L
        );
        assertThat(n.getUserId()).isEqualTo(1L);
        assertThat(n.getType()).isEqualTo(NotificationType.메시지);
        assertThat(n.getTitle()).isEqualTo("새 메시지");
        assertThat(n.getContent()).isEqualTo("안녕");
        assertThat(n.getLinkType()).isEqualTo("chat-room");
        assertThat(n.getLinkId()).isEqualTo(10L);
        assertThat(n.isRead()).isFalse();
    }

    @Test
    @DisplayName("markAsRead")
    void markAsRead() {
        Notification n = Notification.create(1L, NotificationType.시스템, "title", null, null, null);
        n.markAsRead();
        assertThat(n.isRead()).isTrue();
    }

    @Test
    @DisplayName("create 빈 title_거부")
    void create_빈_title_거부() {
        assertThatThrownBy(() ->
                Notification.create(1L, NotificationType.메시지, "", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                Notification.create(1L, NotificationType.메시지, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create null/비양수 userId_거부")
    void create_invalid_user_거부() {
        assertThatThrownBy(() ->
                Notification.create(null, NotificationType.메시지, "t", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                Notification.create(0L, NotificationType.메시지, "t", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("isOwnedBy")
    void isOwnedBy() {
        Notification n = Notification.create(100L, NotificationType.시스템, "t", null, null, null);
        assertThat(n.isOwnedBy(100L)).isTrue();
        assertThat(n.isOwnedBy(200L)).isFalse();
        assertThat(n.isOwnedBy(null)).isFalse();
    }
}
