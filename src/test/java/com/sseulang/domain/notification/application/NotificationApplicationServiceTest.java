package com.sseulang.domain.notification.application;

import com.sseulang.domain.notification.application.dto.NotificationResult;
import com.sseulang.domain.notification.domain.Notification;
import com.sseulang.domain.notification.domain.NotificationType;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationApplicationServiceTest {

    private static final Long USER = 100L;
    private static final Long OTHER = 200L;

    private InMemoryFakeNotificationRepository repo;
    private NotificationApplicationService service;

    @BeforeEach
    void setUp() {
        repo = new InMemoryFakeNotificationRepository();
        service = new NotificationApplicationService(repo);
    }

    @Test
    @DisplayName("notify 정상_저장됨 + read=false")
    void notify_정상() {
        Notification n = service.notify(USER, NotificationType.메시지, "새 메시지", "안녕", "chat-room", 10L);

        assertThat(n.getId()).isNotNull();
        assertThat(n.isRead()).isFalse();
    }

    @Test
    @DisplayName("listMine 본인 알림만_최신순")
    void listMine() {
        service.notify(USER, NotificationType.메시지, "1", null, null, null);
        service.notify(USER, NotificationType.거래, "2", null, null, null);
        service.notify(OTHER, NotificationType.시스템, "X", null, null, null);

        Page<NotificationResult> mine = service.listMine(USER, PageRequest.of(0, 10));

        assertThat(mine.getTotalElements()).isEqualTo(2);
        assertThat(mine.getContent())
                .extracting(NotificationResult::title)
                .containsExactlyInAnyOrder("1", "2");
    }

    @Test
    @DisplayName("markAsRead 본인_정상")
    void markAsRead_정상() {
        Notification n = service.notify(USER, NotificationType.시스템, "t", null, null, null);

        service.markAsRead(n.getId(), USER);

        Notification reloaded = repo.findById(n.getId()).orElseThrow();
        assertThat(reloaded.isRead()).isTrue();
    }

    @Test
    @DisplayName("markAsRead 타인_FORBIDDEN")
    void markAsRead_타인_거부() {
        Notification n = service.notify(USER, NotificationType.시스템, "t", null, null, null);

        assertThatThrownBy(() -> service.markAsRead(n.getId(), OTHER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("markAsRead 없는 알림_RESOURCE_NOT_FOUND")
    void markAsRead_없음() {
        assertThatThrownBy(() -> service.markAsRead("nonexistent", USER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("markAsRead 멱등_이미 읽음 호출도 OK")
    void markAsRead_멱등() {
        Notification n = service.notify(USER, NotificationType.시스템, "t", null, null, null);
        service.markAsRead(n.getId(), USER);
        service.markAsRead(n.getId(), USER);  // 두 번 호출

        Notification reloaded = repo.findById(n.getId()).orElseThrow();
        assertThat(reloaded.isRead()).isTrue();
    }
}
