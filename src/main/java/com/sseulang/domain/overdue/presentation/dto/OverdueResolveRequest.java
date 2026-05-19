package com.sseulang.domain.overdue.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자 강제 종료 요청 — 외부 합의 등.")
public record OverdueResolveRequest(
        @Schema(description = "처리 메모 (선택)", example = "buyer 와 seller 외부 합의 종결",
                maxLength = 1000, nullable = true)
        @Size(max = 1000) String note
) { }
