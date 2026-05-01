package com.sseulang.domain.report.presentation.dto;

import com.sseulang.domain.report.domain.ReportStatus;
import com.sseulang.domain.report.domain.UserReport;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "관리자 — 사용자 신고 단건.")
public record AdminReportResponse(
        @Schema(example = "17") Long id,
        @Schema(example = "100", description = "신고한 사용자") Long reporterId,
        @Schema(example = "200", description = "신고당한 사용자") Long reportedId,
        @Schema(description = "관련 물품 (없으면 null)", example = "42") Long itemId,
        @Schema(example = "FRAUD", description = "FRAUD / SPAM / HARASSMENT 등") String reason,
        @Schema(example = "결제 후 물품을 보내지 않습니다") String detail,
        ReportStatus status,
        @Schema(description = "처리한 관리자 (PENDING 단계는 null)") Long adminId,
        @Schema(example = "확인 후 경고 처리") String adminMemo,
        LocalDateTime processedAt,
        LocalDateTime createdAt
) {
    public static AdminReportResponse from(UserReport r) {
        return new AdminReportResponse(
                r.getId(), r.getReporterId(), r.getReportedId(), r.getItemId(),
                r.getReason(), r.getDetail(), r.getStatus(),
                r.getAdminId(), r.getAdminMemo(), r.getProcessedAt(), r.getCreatedAt()
        );
    }
}
