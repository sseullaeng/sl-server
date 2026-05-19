package com.sseulang.domain.notice.application.dto;

import com.sseulang.domain.notice.domain.NoticeType;

import java.time.LocalDateTime;

public record NoticeUpsertCommand(
        NoticeType type,
        String title,
        String content,
        String imageUrl,
        LocalDateTime startsAt,
        LocalDateTime endsAt
) {}
