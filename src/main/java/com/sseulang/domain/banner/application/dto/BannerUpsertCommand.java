package com.sseulang.domain.banner.application.dto;

import java.time.LocalDateTime;

public record BannerUpsertCommand(
        String title,
        String imageUrl,
        String linkUrl,
        int sortOrder,
        LocalDateTime startsAt,
        LocalDateTime endsAt
) {}
