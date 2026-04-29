package com.sseulang.domain.notification.application.dto;

import com.sseulang.domain.notification.domain.Notification;
import com.sseulang.domain.notification.domain.NotificationType;

import java.time.Instant;

public record NotificationResult(
        String id,
        Long userId,
        NotificationType type,
        String title,
        String content,
        String linkType,
        Long linkId,
        boolean read,
        Instant createdAt
) {
    public static NotificationResult from(Notification n) {
        return new NotificationResult(
                n.getId(), n.getUserId(), n.getType(),
                n.getTitle(), n.getContent(),
                n.getLinkType(), n.getLinkId(),
                n.isRead(),
                n.getCreatedAt()
        );
    }
}
