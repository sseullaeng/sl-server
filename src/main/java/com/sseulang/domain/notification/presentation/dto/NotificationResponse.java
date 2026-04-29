package com.sseulang.domain.notification.presentation.dto;

import com.sseulang.domain.notification.application.dto.NotificationResult;
import com.sseulang.domain.notification.domain.NotificationType;

import java.time.Instant;

public record NotificationResponse(
        String id,
        NotificationType type,
        String title,
        String content,
        String linkType,
        Long linkId,
        boolean read,
        Instant createdAt
) {
    public static NotificationResponse from(NotificationResult r) {
        return new NotificationResponse(
                r.id(), r.type(),
                r.title(), r.content(),
                r.linkType(), r.linkId(),
                r.read(),
                r.createdAt()
        );
    }
}
