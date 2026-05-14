package com.sseulang.domain.report.presentation;

import com.sseulang.domain.report.domain.ReportStatus;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/**
 * 신고 status 쿼리 파라미터 — 한글 정식값 + 영어 alias 둘 다 허용.
 * FE 가 PENDING/IN_PROGRESS/COMPLETED/REJECTED 송신해도 한글 enum 으로 매핑.
 */
@Component
public class StringToReportStatusConverter implements Converter<String, ReportStatus> {

    private static final Map<String, ReportStatus> ALIAS = Map.of(
            "PENDING", ReportStatus.접수,
            "IN_PROGRESS", ReportStatus.처리중,
            "COMPLETED", ReportStatus.처리완료,
            "REJECTED", ReportStatus.반려
    );

    @Override
    public ReportStatus convert(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        String s = source.trim();
        ReportStatus alias = ALIAS.get(s.toUpperCase(Locale.ROOT));
        if (alias != null) {
            return alias;
        }
        return ReportStatus.valueOf(s);
    }
}
