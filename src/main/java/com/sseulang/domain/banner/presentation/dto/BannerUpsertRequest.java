package com.sseulang.domain.banner.presentation.dto;

import com.sseulang.domain.banner.application.dto.BannerUpsertCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record BannerUpsertRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 500) String imageUrl,
        @Size(max = 500) String linkUrl,
        int sortOrder,
        LocalDateTime startsAt,
        LocalDateTime endsAt
) {
    public BannerUpsertCommand toCommand() {
        return new BannerUpsertCommand(title, imageUrl, linkUrl, sortOrder, startsAt, endsAt);
    }
}
