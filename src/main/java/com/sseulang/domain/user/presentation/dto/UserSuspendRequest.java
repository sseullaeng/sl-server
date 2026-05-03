package com.sseulang.domain.user.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "관리자 시한부 활동정지 요청. days >= 1.")
public record UserSuspendRequest(
        @Schema(example = "7", description = "정지 기간(일)")
        @Min(1) @Max(365) int days
) { }
