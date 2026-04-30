package com.sseulang.domain.report.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "사용자/물품 신고. reason 은 짧은 분류, detail 은 자세한 설명.")
public record ReportRequest(
        @Schema(description = "신고 사유 분류", example = "사기 의심", maxLength = 50)
        @NotBlank @Size(max = 50) String reason,

        @Schema(description = "상세 설명 (선택)", example = "결제 후 연락 끊김. 채팅 내역 첨부.", maxLength = 5000, nullable = true)
        @Size(max = 5000) String detail
) { }
