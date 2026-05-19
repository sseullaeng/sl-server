package com.sseulang.domain.report.application;

import com.sseulang.domain.report.domain.ReportStatus;
import com.sseulang.domain.report.domain.UserReport;
import com.sseulang.domain.report.domain.UserReportRepository;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class UserReportApplicationService {

    private final UserReportRepository repository;
    private final com.sseulang.domain.user.application.UserApplicationService userService;
    private final Clock clock;

    public UserReportApplicationService(
            UserReportRepository repository,
            com.sseulang.domain.user.application.UserApplicationService userService,
            Clock clock
    ) {
        this.repository = repository;
        this.userService = userService;
        this.clock = clock;
    }

    

    @Transactional
    public Long reportUser(Long reporterId, Long reportedUserId, String reason, String detail) {
        userService.requireVerified(reporterId);
        return repository.save(UserReport.reportUser(reporterId, reportedUserId, reason, detail)).getId();
    }

    
    @Transactional
    public Long reportItem(Long reporterId, Long itemId, String reason, String detail) {
        userService.requireVerified(reporterId);
        return repository.save(UserReport.reportItem(reporterId, itemId, reason, detail)).getId();
    }

    

    @Transactional
    public void adminMarkInProgress(Long reportId, Long adminId, String memo) {
        UserReport r = findOrThrow(reportId);
        r.markInProgress(adminId, memo, LocalDateTime.now(clock));
    }

    @Transactional
    public void adminComplete(Long reportId, Long adminId, String memo) {
        UserReport r = findOrThrow(reportId);
        r.complete(adminId, memo, LocalDateTime.now(clock));
    }

    @Transactional
    public void adminReject(Long reportId, Long adminId, String memo) {
        UserReport r = findOrThrow(reportId);
        r.reject(adminId, memo, LocalDateTime.now(clock));
    }

    public Page<UserReport> adminFindByStatus(ReportStatus status, Pageable pageable) {
        return repository.findByStatus(status, pageable);
    }

    public UserReport adminFindById(Long reportId) {
        return findOrThrow(reportId);
    }

    
    public long countPending() {
        return repository.countPending();
    }

    
    public long countResolved() {
        return repository.countResolved();
    }

    
    public long countCreatedSince(java.time.LocalDateTime since) {
        return repository.countCreatedSince(since);
    }

    private UserReport findOrThrow(Long reportId) {
        return repository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
    }
}
