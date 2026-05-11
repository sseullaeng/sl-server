package com.sseulang.domain.report.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface UserReportRepository {

    UserReport save(UserReport report);

    Optional<UserReport> findById(Long id);

    
    Page<UserReport> findByStatus(ReportStatus status, Pageable pageable);

    

    java.util.Map<Long, Long> countByTargetUserIds(java.util.Collection<Long> targetUserIds);

    
    long countPending();

    
    long countResolved();

    
    long countCreatedSince(java.time.LocalDateTime since);
}
