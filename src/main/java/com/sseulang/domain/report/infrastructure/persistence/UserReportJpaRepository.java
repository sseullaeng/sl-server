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
}
