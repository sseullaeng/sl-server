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

    
    @Query("SELECT COUNT(r) FROM UserReport r WHERE r.status IN (com.sseulang.domain.report.domain.ReportStatus.접수, com.sseulang.domain.report.domain.ReportStatus.처리중)")
    long countPending();

    
    @Query("SELECT COUNT(r) FROM UserReport r WHERE r.status IN (com.sseulang.domain.report.domain.ReportStatus.처리완료, com.sseulang.domain.report.domain.ReportStatus.반려)")
    long countResolved();

    
    @Query("SELECT COUNT(r) FROM UserReport r WHERE r.createdAt >= :since")
    long countCreatedSince(@org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since);

    // 라운드 12 — admin item 화면. itemId 별 신고 누적 카운트.
    @Query("""
            SELECT r.itemId AS itemId, COUNT(r) AS cnt FROM UserReport r
             WHERE r.itemId IN :ids
             GROUP BY r.itemId
            """)
    java.util.List<ItemCountRow> countByItemIdsRaw(@Param("ids") java.util.Collection<Long> ids);

    interface ItemCountRow {
        Long getItemId();
        Long getCnt();
    }

    java.util.List<UserReport> findByItemIdOrderByCreatedAtDesc(Long itemId);
}
