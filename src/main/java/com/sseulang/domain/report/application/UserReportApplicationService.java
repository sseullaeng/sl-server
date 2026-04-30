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

    /**
     * 사용자 신고. 자기 신고 거부는 도메인 invariant 에서.
     * 미인증 사용자의 무차별 신고 스팸 방지 — verified 가드 (게이트 1 round 2).
     */
    @Transactional
    public Long reportUser(Long reporterId, Long reportedUserId, String reason, String detail) {
        userService.requireVerified(reporterId);
        return repository.save(UserReport.reportUser(reporterId, reportedUserId, reason, detail)).getId();
    }

    /** 물품 신고. Item 존재 여부는 FK 가 잡음 (RESTRICT/CASCADE) — 별도 검증 생략. */
    @Transactional
    public Long reportItem(Long reporterId, Long itemId, String reason, String detail) {
        userService.requireVerified(reporterId);
        return repository.save(UserReport.reportItem(reporterId, itemId, reason, detail)).getId();
    }

    // ───────── 관리자 처리 흐름 ─────────

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

    private UserReport findOrThrow(Long reportId) {
        return repository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
    }
}
