package com.sseulang.domain.report.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "신고 처리 상태. 접수→처리중→처리완료/반려.")
public enum ReportStatus {
    접수,
    처리중,
    처리완료,
    반려;

    public boolean isTerminal() {
        return this == 처리완료 || this == 반려;
    }
}
