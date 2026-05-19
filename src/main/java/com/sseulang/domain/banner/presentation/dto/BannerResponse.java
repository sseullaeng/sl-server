package com.sseulang.domain.banner.presentation.dto;

import com.sseulang.domain.banner.application.dto.BannerResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "메인 배너 — 클릭 시 linkUrl 로 이동. active+startsAt~endsAt 로 노출 제어.")
public record BannerResponse(
        @Schema(example = "2") Long id,
        @Schema(example = "5월 봄맞이 이벤트") String title,
        @Schema(example = "https://cdn.sseulang.com/banners/1/spring.jpg") String imageUrl,
        @Schema(example = "/events/spring") String linkUrl,
        @Schema(example = "1", description = "정렬 순서 (작을수록 앞)") int sortOrder,
        @Schema(example = "true") boolean active,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        LocalDateTime createdAt
) {
    public static BannerResponse from(BannerResult r) {
        return new BannerResponse(r.id(), r.title(), r.imageUrl(), r.linkUrl(),
                r.sortOrder(), r.active(), r.startsAt(), r.endsAt(), r.createdAt());
    }
}
