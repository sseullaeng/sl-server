package com.sseulang.domain.report.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "신고 등록 후 발급된 id 응답.")
public record ReportIdResponse(@Schema(example = "17") Long id) { }
