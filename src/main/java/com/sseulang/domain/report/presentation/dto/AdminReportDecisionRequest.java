package com.sseulang.domain.report.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자 신고 처리. MARK_IN_PROGRESS(검토 시작) / COMPLETE(처리 완료) / REJECT(반려).")
public record AdminReportDecisionRequest(
        @Schema(description = "처리 액션", example = "MARK_IN_PROGRESS",
                allowableValues = {"MARK_IN_PROGRESS", "COMPLETE", "REJECT"})
        @NotNull Action action,

        @Schema(description = "처리 메모 (선택)", example = "사용자 차단 처리 완료", maxLength = 500, nullable = true)
        @Size(max = 500) String memo
) {
    public enum Action { MARK_IN_PROGRESS, COMPLETE, REJECT }
}
