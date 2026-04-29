package com.sseulang.domain.report.application;

import com.sseulang.domain.report.domain.UserReport;
import com.sseulang.domain.report.domain.UserReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserReportApplicationService {

    private final UserReportRepository repository;

    public UserReportApplicationService(UserReportRepository repository) {
        this.repository = repository;
    }

    /** 사용자 신고. 자기 신고 거부는 도메인 invariant 에서. */
    @Transactional
    public Long reportUser(Long reporterId, Long reportedUserId, String reason, String detail) {
        return repository.save(UserReport.reportUser(reporterId, reportedUserId, reason, detail)).getId();
    }

    /** 물품 신고. Item 존재 여부는 FK 가 잡음 (RESTRICT/CASCADE) — 별도 검증 생략. */
    @Transactional
    public Long reportItem(Long reporterId, Long itemId, String reason, String detail) {
        return repository.save(UserReport.reportItem(reporterId, itemId, reason, detail)).getId();
    }
}
