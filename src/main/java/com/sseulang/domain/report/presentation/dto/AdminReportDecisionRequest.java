package com.sseulang.domain.report.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminReportDecisionRequest(
        @NotNull Action action,
        @Size(max = 500) String memo
) {
    public enum Action { MARK_IN_PROGRESS, COMPLETE, REJECT }
}
