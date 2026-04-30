package com.sseulang.domain.report.presentation.dto;

import com.sseulang.domain.report.domain.ReportStatus;
import com.sseulang.domain.report.domain.UserReport;

import java.time.LocalDateTime;

public record AdminReportResponse(
        Long id,
        Long reporterId,
        Long reportedId,
        Long itemId,
        String reason,
        String detail,
        ReportStatus status,
        Long adminId,
        String adminMemo,
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
