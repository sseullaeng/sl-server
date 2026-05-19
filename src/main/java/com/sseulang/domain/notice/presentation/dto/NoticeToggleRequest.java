package com.sseulang.domain.notice.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "공지 pin/publish 토글. 엔드포인트에 따라 의미가 달라짐.")
public record NoticeToggleRequest(
        @Schema(description = "켜기/끄기", example = "true")
        @NotNull Boolean value
) {}
