package com.sseulang.domain.message.application.event;

import com.sseulang.domain.message.application.dto.MessageResult;
import com.sseulang.domain.notification.application.dto.NotificationResult;

public record ChatRealtimePublishRequestedEvent(
        Long chatRoomId,
        Long opponentId,
        MessageResult message,
        NotificationResult notification
) {
}
