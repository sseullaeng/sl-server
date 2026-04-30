package com.sseulang.domain.report.domain;

import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserReportTest {

    @Test
    @DisplayName("reportUser 정상_status=접수")
    void reportUser_정상() {
        UserReport r = UserReport.reportUser(1L, 2L, "스팸", "광고 메시지 반복");
        assertThat(r.getReporterId()).isEqualTo(1L);
        assertThat(r.getReportedId()).isEqualTo(2L);
        assertThat(r.getItemId()).isNull();
        assertThat(r.getReason()).isEqualTo("스팸");
        assertThat(r.getStatus()).isEqualTo(ReportStatus.접수);
    }

    @Test
    @DisplayName("reportItem 정상_reportedId null + itemId 박힘")
    void reportItem_정상() {
        UserReport r = UserReport.reportItem(1L, 99L, "허위매물", "사진과 다름");
        assertThat(r.getItemId()).isEqualTo(99L);
        assertThat(r.getReportedId()).isNull();
    }

    @Test
    @DisplayName("reportUser 자기 신고_거부")
    void reportUser_self_거부() {
        assertThatThrownBy(() -> UserReport.reportUser(1L, 1L, "x", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("reportUser null/비양수 reportedId_거부")
    void reportUser_null_거부() {
        assertThatThrownBy(() -> UserReport.reportUser(1L, null, "x", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserReport.reportUser(1L, 0L, "x", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create 빈 reason_거부")
    void create_빈_reason_거부() {
        assertThatThrownBy(() -> UserReport.reportUser(1L, 2L, "", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserReport.reportUser(1L, 2L, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserReport.reportUser(1L, 2L, "   ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create reason 50자 초과_거부")
    void create_reason_길이초과_거부() {
        String tooLong = "가".repeat(51);
        assertThatThrownBy(() -> UserReport.reportUser(1L, 2L, tooLong, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ───────── 관리자 처리 흐름 ─────────
    private static final Long ADMIN = 99L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 4, 30, 12, 0);

    private UserReport pending() {
        return UserReport.reportUser(1L, 2L, "스팸", null);
    }

    @Test
    @DisplayName("markInProgress 접수 → 처리중_adminId/processedAt 기록")
    void markInProgress_정상() {
        UserReport r = pending();
        r.markInProgress(ADMIN, "검토 시작", NOW);

        assertThat(r.getStatus()).isEqualTo(ReportStatus.처리중);
        assertThat(r.getAdminId()).isEqualTo(ADMIN);
        assertThat(r.getAdminMemo()).isEqualTo("검토 시작");
        assertThat(r.getProcessedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("markInProgress 처리중·terminal 상태_REPORT_INVALID_STATE")
    void markInProgress_상태_거부() {
        UserReport r = pending();
        r.markInProgress(ADMIN, null, NOW);
        assertThatThrownBy(() -> r.markInProgress(ADMIN, null, NOW.plusMinutes(1)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REPORT_INVALID_STATE);
    }

    @Test
    @DisplayName("complete 처리중 → 처리완료")
    void complete_정상() {
        UserReport r = pending();
        r.markInProgress(ADMIN, null, NOW);
        r.complete(ADMIN, "처리 완료", NOW.plusMinutes(10));

        assertThat(r.getStatus()).isEqualTo(ReportStatus.처리완료);
        assertThat(r.getStatus().isTerminal()).isTrue();
    }

    @Test
    @DisplayName("complete 접수 상태_REPORT_INVALID_STATE")
    void complete_접수_거부() {
        UserReport r = pending();
        assertThatThrownBy(() -> r.complete(ADMIN, null, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REPORT_INVALID_STATE);
    }

    @Test
    @DisplayName("reject 접수 → 반려, 처리중 → 반려")
    void reject_정상() {
        UserReport r1 = pending();
        r1.reject(ADMIN, "근거 부족", NOW);
        assertThat(r1.getStatus()).isEqualTo(ReportStatus.반려);

        UserReport r2 = pending();
        r2.markInProgress(ADMIN, null, NOW);
        r2.reject(ADMIN, "근거 부족", NOW.plusMinutes(1));
        assertThat(r2.getStatus()).isEqualTo(ReportStatus.반려);
    }

    @Test
    @DisplayName("reject terminal 상태_REPORT_INVALID_STATE")
    void reject_terminal_거부() {
        UserReport r = pending();
        r.reject(ADMIN, null, NOW);
        assertThatThrownBy(() -> r.reject(ADMIN, null, NOW.plusMinutes(1)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REPORT_INVALID_STATE);
    }
}
