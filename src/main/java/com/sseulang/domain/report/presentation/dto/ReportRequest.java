package com.sseulang.domain.report.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReportRequest(
        @NotBlank @Size(max = 50) String reason,
        @Size(max = 5000) String detail
) { }
