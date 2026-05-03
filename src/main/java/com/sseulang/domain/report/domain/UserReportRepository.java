package com.sseulang.domain.report.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface UserReportRepository {

    UserReport save(UserReport report);

    Optional<UserReport> findById(Long id);

    /** 관리자 — 상태별 페이징 (status null 이면 전체). created_at DESC. */
    Page<UserReport> findByStatus(ReportStatus status, Pageable pageable);

    /**
     * Admin 회원 카드용 — targetUserIds 가 신고당한 횟수의 (targetUserId → count) 맵.
     * 빈 입력은 빈 맵. 단일 GROUP BY 쿼리 — N+1 회피.
     */
    java.util.Map<Long, Long> countByTargetUserIds(java.util.Collection<Long> targetUserIds);
}
