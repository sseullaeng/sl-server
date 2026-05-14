package com.sseulang.domain.user.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자 포인트 지급 요청")
public record AdminUserPointCreditRequest(
        @NotNull
        @Positive
        @Schema(example = "5000", description = "지급할 포인트 금액 (KRW)")
        Long amount,
        @Size(max = 120)
        @Schema(example = "이벤트 보상", description = "지급 사유", nullable = true)
        String reason
) { }
