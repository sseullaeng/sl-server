package com.sseulang.domain.report.infrastructure.persistence;

import com.sseulang.domain.report.domain.ReportStatus;
import com.sseulang.domain.report.domain.UserReport;
import com.sseulang.domain.report.domain.UserReportRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserReportRepositoryImpl implements UserReportRepository {

    private final UserReportJpaRepository jpa;

    public UserReportRepositoryImpl(UserReportJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public UserReport save(UserReport report) {
        return jpa.save(report);
    }

    @Override
    public Optional<UserReport> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Page<UserReport> findByStatus(ReportStatus status, Pageable pageable) {
        return jpa.findByStatusFilter(status, pageable);
    }

    @Override
    public java.util.Map<Long, Long> countByTargetUserIds(java.util.Collection<Long> targetUserIds) {
        if (targetUserIds == null || targetUserIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        java.util.Map<Long, Long> result = new java.util.HashMap<>();
        for (UserReportJpaRepository.UserCountRow row : jpa.countByTargetUserIdsRaw(targetUserIds)) {
            result.put(row.getUserId(), row.getCnt());
        }
        return result;
    }
}
