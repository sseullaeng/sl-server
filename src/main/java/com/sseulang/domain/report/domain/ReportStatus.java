package com.sseulang.domain.report.domain;

import io.swagger.v3.oas.annotations.media.Schema;
/**
 * 신고 처리 상태. DB ENUM('접수','처리중','처리완료','반려') 1:1.
 * 상태 전이는 관리자가 트리거 (Day 9 admin 도메인).
 */
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
