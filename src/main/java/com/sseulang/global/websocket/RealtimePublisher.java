package com.sseulang.global.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 실시간 broadcast 인프라 — STOMP topic / user destination 발송. 도메인 ApplicationService 가
 * 본 컴포넌트에 의존하지 않도록 인터페이스 분리도 가능하나, 현재는 단일 구현이라 직접 노출.
 *
 * <p>가이드 §4.10 토픽 구조:
 * <ul>
 *   <li>{@code /topic/chat-room/{roomId}} — 채팅방 메시지 fan-out</li>
 *   <li>{@code /user/{userId}/queue/notifications} — 사용자 알림 (Spring 자동 라우팅)</li>
 * </ul></p>
 */
@Component
public class RealtimePublisher {

    private final SimpMessagingTemplate template;

    public RealtimePublisher(SimpMessagingTemplate template) {
        this.template = template;
    }

    public void publishToChatRoom(Long roomId, Object payload) {
        template.convertAndSend("/topic/chat-room/" + roomId, payload);
    }

    public void publishNotification(Long userId, Object payload) {
        // Spring UserDestinationMessageHandler 가 /user/{userId}/queue/notifications 로 라우팅.
        template.convertAndSendToUser(String.valueOf(userId), "/queue/notifications", payload);
    }
}
