package com.sseulang.domain.report.presentation;

import com.sseulang.domain.report.domain.ReportStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StringToReportStatusConverterTest {

    private final StringToReportStatusConverter converter = new StringToReportStatusConverter();

    @Test
    @DisplayName("convert_한글정식값_정상매핑")
    void convert_korean() {
        assertThat(converter.convert("접수")).isEqualTo(ReportStatus.접수);
        assertThat(converter.convert("처리중")).isEqualTo(ReportStatus.처리중);
        assertThat(converter.convert("처리완료")).isEqualTo(ReportStatus.처리완료);
        assertThat(converter.convert("반려")).isEqualTo(ReportStatus.반려);
    }

    @Test
    @DisplayName("convert_영어 alias_대소문자 무관 정상매핑")
    void convert_english_alias() {
        assertThat(converter.convert("PENDING")).isEqualTo(ReportStatus.접수);
        assertThat(converter.convert("pending")).isEqualTo(ReportStatus.접수);
        assertThat(converter.convert("IN_PROGRESS")).isEqualTo(ReportStatus.처리중);
        assertThat(converter.convert("COMPLETED")).isEqualTo(ReportStatus.처리완료);
        assertThat(converter.convert("REJECTED")).isEqualTo(ReportStatus.반려);
    }

    @Test
    @DisplayName("convert_null/blank_null 반환")
    void convert_null_or_blank() {
        assertThat(converter.convert(null)).isNull();
        assertThat(converter.convert("")).isNull();
        assertThat(converter.convert("  ")).isNull();
    }

    @Test
    @DisplayName("convert_unknown_IllegalArgumentException")
    void convert_unknown() {
        assertThatThrownBy(() -> converter.convert("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
