package com.sseulang.domain.banner.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "배너 활성/비활성 토글.")
public record BannerActiveRequest(
        @Schema(description = "활성 여부", example = "true")
        @NotNull Boolean active
) {}
