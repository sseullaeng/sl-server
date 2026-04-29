package com.sseulang.domain.report.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
