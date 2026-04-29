package com.sseulang.global.websocket;

import java.util.ArrayList;
import java.util.List;

/** 테스트용 — broadcast 호출을 in-memory 로 기록만. SimpMessagingTemplate 의존 X. */
public class FakeRealtimePublisher extends RealtimePublisher {

    public final List<ChatRoomPublication> chatRoomPublications = new ArrayList<>();
    public final List<NotificationPublication> notificationPublications = new ArrayList<>();

    public FakeRealtimePublisher() {
        super(null);  // SimpMessagingTemplate 미사용 — 부모 필드만 null 저장하므로 안전
    }

    @Override
    public void publishToChatRoom(Long roomId, Object payload) {
        chatRoomPublications.add(new ChatRoomPublication(roomId, payload));
    }

    @Override
    public void publishNotification(Long userId, Object payload) {
        notificationPublications.add(new NotificationPublication(userId, payload));
    }

    public record ChatRoomPublication(Long roomId, Object payload) { }

    public record NotificationPublication(Long userId, Object payload) { }
}
