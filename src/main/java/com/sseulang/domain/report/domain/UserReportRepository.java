package com.sseulang.domain.report.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface UserReportRepository {

    UserReport save(UserReport report);

    Optional<UserReport> findById(Long id);

    /** 관리자 — 상태별 페이징 (status null 이면 전체). created_at DESC. */
    Page<UserReport> findByStatus(ReportStatus status, Pageable pageable);
}
