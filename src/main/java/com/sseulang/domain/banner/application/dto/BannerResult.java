package com.sseulang.domain.banner.application.dto;

import com.sseulang.domain.banner.domain.Banner;

import java.time.LocalDateTime;

public record BannerResult(
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
    public static BannerResult from(Banner b) {
        return new BannerResult(
                b.getId(), b.getAdminId(), b.getTitle(), b.getImageUrl(), b.getLinkUrl(),
                b.getSortOrder(), b.isActive(), b.getStartsAt(), b.getEndsAt(), b.getCreatedAt()
        );
    }
}
