package com.sseulang.domain.message.application;

import com.sseulang.domain.message.application.event.ChatRealtimePublishRequestedEvent;
import com.sseulang.global.websocket.RealtimePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ChatRealtimeEventListener {

    private static final Logger log = LoggerFactory.getLogger(ChatRealtimeEventListener.class);

    private final RealtimePublisher realtimePublisher;

    public ChatRealtimeEventListener(RealtimePublisher realtimePublisher) {
        this.realtimePublisher = realtimePublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPublish(ChatRealtimePublishRequestedEvent event) {
        try {
            realtimePublisher.publishNotification(event.opponentId(), event.notification());
        } catch (RuntimeException e) {
            log.warn("[chat-realtime] notification publish 실패 opponentId={}", event.opponentId(), e);
        }
        try {
            realtimePublisher.publishToChatRoom(event.chatRoomId(), event.message());
        } catch (RuntimeException e) {
            log.warn("[chat-realtime] chat-room broadcast 실패 chatRoomId={}", event.chatRoomId(), e);
        }
    }
}
