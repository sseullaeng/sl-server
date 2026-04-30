package com.sseulang.domain.banner.presentation.dto;

import com.sseulang.domain.banner.application.dto.BannerResult;

import java.time.LocalDateTime;

public record BannerResponse(
        Long id,
        Long adminId,
        String title,
        String imageUrl,
        String linkUrl,
        int sortOrder,
        boolean active,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        LocalDateTime createdAt
) {
    public static BannerResponse from(BannerResult r) {
        return new BannerResponse(r.id(), r.adminId(), r.title(), r.imageUrl(), r.linkUrl(),
                r.sortOrder(), r.active(), r.startsAt(), r.endsAt(), r.createdAt());
    }
}
