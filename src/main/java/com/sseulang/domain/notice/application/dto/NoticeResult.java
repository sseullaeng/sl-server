package com.sseulang.domain.notice.application.dto;

import com.sseulang.domain.notice.domain.Notice;
import com.sseulang.domain.notice.domain.NoticeType;

import java.time.LocalDateTime;

/**
 * 공지 단건 Result — admin / user 공용. user 응답에서는 isPublished 가 항상 true 로 고정되지만
 * 필드는 그대로 노출 (admin 화면 재사용성 + 디버깅 용이).
 */
public record NoticeResult(
        Long id,
        Long adminId,
        NoticeType type,
        String title,
        String content,
        String imageUrl,
        boolean pinned,
        boolean published,
        int viewCount,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        LocalDateTime createdAt
) {
    public static NoticeResult from(Notice n) {
        return new NoticeResult(
                n.getId(),
                n.getAdminId(),
                n.getType(),
                n.getTitle(),
                n.getContent(),
                n.getImageUrl(),
                n.isPinned(),
                n.isPublished(),
                n.getViewCount(),
                n.getStartsAt(),
                n.getEndsAt(),
                n.getCreatedAt()
        );
    }
}
