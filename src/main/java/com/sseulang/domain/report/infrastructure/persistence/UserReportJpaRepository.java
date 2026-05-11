package com.sseulang.domain.report.infrastructure.persistence;

import com.sseulang.domain.report.domain.ReportStatus;
import com.sseulang.domain.report.domain.UserReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserReportJpaRepository extends JpaRepository<UserReport, Long> {

    @Query("""
            SELECT r FROM UserReport r
             WHERE (:status IS NULL OR r.status = :status)
             ORDER BY r.id DESC
            """)
    Page<UserReport> findByStatusFilter(@Param("status") ReportStatus status, Pageable pageable);

    /** Admin enrich — targetUserIds 의 신고 누적 수 (target_user_id, count). */
    @Query("""
            SELECT r.reportedId AS userId, COUNT(r) AS cnt FROM UserReport r
             WHERE r.reportedId IN :ids
             GROUP BY r.reportedId
            """)
    java.util.List<UserCountRow> countByTargetUserIdsRaw(@Param("ids") java.util.Collection<Long> ids);

    interface UserCountRow {
        Long getUserId();
        Long getCnt();
    }

    /** 차트 dashboard summary — 처리 대기 (접수 또는 처리중) 신고 수. */
    @Query("SELECT COUNT(r) FROM UserReport r WHERE r.status IN (com.sseulang.domain.report.domain.ReportStatus.접수, com.sseulang.domain.report.domain.ReportStatus.처리중)")
    long countPending();

    /** 차트 dashboard — 처리 완료 (처리완료 또는 반려) 신고 수. */
    @Query("SELECT COUNT(r) FROM UserReport r WHERE r.status IN (com.sseulang.domain.report.domain.ReportStatus.처리완료, com.sseulang.domain.report.domain.ReportStatus.반려)")
    long countResolved();

    /** 차트 dashboard — 특정 시점 이후 생성된 신고 건수 (전체 status). */
    @Query("SELECT COUNT(r) FROM UserReport r WHERE r.createdAt >= :since")
    long countCreatedSince(@org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since);
}
