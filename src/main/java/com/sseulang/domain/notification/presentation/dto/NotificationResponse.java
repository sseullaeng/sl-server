package com.sseulang.domain.notification.presentation.dto;

import com.sseulang.domain.notification.application.dto.NotificationResult;
import com.sseulang.domain.notification.domain.NotificationCategory;
import com.sseulang.domain.notification.domain.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "알림 — MongoDB 저장. linkType+linkId 로 클라이언트가 화면 라우팅.")
public record NotificationResponse(
        @Schema(example = "65a1b2c3d4e5f60001234567") String id,
        NotificationType type,
        @Schema(example = "USER", description = "대분류 — SYSTEM/REPORT/INQUIRY/USER (라운드 12 PR-F #5)")
        NotificationCategory category,
        @Schema(example = "새 메시지") String title,
        @Schema(example = "안녕하세요, 거래 가능할까요?") String content,
        @Schema(example = "CHAT_ROOM", description = "라우팅 종류 (CHAT_ROOM / TRANSACTION / DELIVERY 등)") String linkType,
        @Schema(example = "8") Long linkId,
        @Schema(description = "읽음 여부", example = "false") boolean read,
        Instant createdAt
) {
    public static NotificationResponse from(NotificationResult r) {
        return new NotificationResponse(
                r.id(), r.type(), r.category(),
                r.title(), r.content(),
                r.linkType(), r.linkId(),
                r.read(),
                r.createdAt()
        );
    }
}
