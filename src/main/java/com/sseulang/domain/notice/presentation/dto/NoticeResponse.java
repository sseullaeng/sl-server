package com.sseulang.domain.notice.presentation.dto;

import com.sseulang.domain.notice.application.dto.NoticeResult;
import com.sseulang.domain.notice.domain.NoticeType;

import java.time.LocalDateTime;

public record NoticeResponse(
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
    public static NoticeResponse from(NoticeResult r) {
        return new NoticeResponse(
                r.id(), r.adminId(), r.type(), r.title(), r.content(), r.imageUrl(),
                r.pinned(), r.published(), r.viewCount(), r.startsAt(), r.endsAt(), r.createdAt()
        );
    }
}
