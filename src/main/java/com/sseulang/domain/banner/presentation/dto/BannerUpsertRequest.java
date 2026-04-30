package com.sseulang.domain.banner.presentation.dto;

import com.sseulang.domain.banner.application.dto.BannerUpsertCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Schema(description = "배너 등록/수정 (관리자). startsAt~endsAt 사이만 active. sortOrder 작을수록 상단.")
public record BannerUpsertRequest(
        @Schema(description = "배너 제목", example = "여름 세일", maxLength = 200)
        @NotBlank @Size(max = 200) String title,

        @Schema(description = "배너 이미지 URL", example = "https://cdn.sseulang.test/banners/summer.jpg", maxLength = 500)
        @NotBlank @Size(max = 500) String imageUrl,

        @Schema(description = "클릭 시 이동 URL (선택)", example = "https://sseulang.test/events/summer", maxLength = 500, nullable = true)
        @Size(max = 500) String linkUrl,

        @Schema(description = "노출 정렬 순서 (작을수록 상단)", example = "10")
        int sortOrder,

        @Schema(description = "노출 시작 시각 (선택, null이면 즉시)", example = "2026-05-01T00:00:00", nullable = true)
        LocalDateTime startsAt,

        @Schema(description = "노출 종료 시각 (선택, null이면 무기한)", example = "2026-05-31T23:59:59", nullable = true)
        LocalDateTime endsAt
) {
    public BannerUpsertCommand toCommand() {
        return new BannerUpsertCommand(title, imageUrl, linkUrl, sortOrder, startsAt, endsAt);
    }
}
