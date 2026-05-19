package com.sseulang.domain.report.domain;

import com.sseulang.global.common.BaseEntity;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserReport extends BaseEntity {

    private static final int REASON_MAX_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Column(name = "reported_id")
    private Long reportedId;

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "reason", nullable = false, length = REASON_MAX_LENGTH)
    private String reason;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReportStatus status;

    @Column(name = "admin_id")
    private Long adminId;

    @Column(name = "admin_memo", length = 500)
    private String adminMemo;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    public static UserReport reportUser(Long reporterId, Long reportedUserId, String reason, String detail) {
        if (reportedUserId == null || reportedUserId <= 0) {
            throw new IllegalArgumentException("reportedUserId 는 양수여야 합니다");
        }
        if (reporterId.equals(reportedUserId)) {
            throw new IllegalArgumentException("자기 자신은 신고할 수 없습니다");
        }
        return create(reporterId, reportedUserId, null, reason, detail);
    }

    public static UserReport reportItem(Long reporterId, Long itemId, String reason, String detail) {
        if (itemId == null || itemId <= 0) {
            throw new IllegalArgumentException("itemId 는 양수여야 합니다");
        }
        return create(reporterId, null, itemId, reason, detail);
    }

    private static UserReport create(Long reporterId, Long reportedId, Long itemId, String reason, String detail) {
        if (reporterId == null || reporterId <= 0) {
            throw new IllegalArgumentException("reporterId 는 양수여야 합니다");
        }
        if (reportedId == null && itemId == null) {
            throw new IllegalArgumentException("reportedId 또는 itemId 둘 중 하나는 필수입니다");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason 은 비어있을 수 없습니다");
        }
        if (reason.length() > REASON_MAX_LENGTH) {
            throw new IllegalArgumentException("reason 은 " + REASON_MAX_LENGTH + "자를 초과할 수 없습니다");
        }
        UserReport r = new UserReport();
        r.reporterId = reporterId;
        r.reportedId = reportedId;
        r.itemId = itemId;
        r.reason = reason.strip();
        r.detail = detail;
        r.status = ReportStatus.접수;
        return r;
    }

    

    public void markInProgress(Long adminId, String memo, LocalDateTime now) {
        validateAdminAndNow(adminId, now);
        if (status != ReportStatus.접수) {
            throw new BusinessException(ErrorCode.REPORT_INVALID_STATE);
        }
        this.status = ReportStatus.처리중;
        this.adminId = adminId;
        this.adminMemo = memo;
        this.processedAt = now;
    }

    
    public void complete(Long adminId, String memo, LocalDateTime now) {
        validateAdminAndNow(adminId, now);
        if (status != ReportStatus.처리중) {
            throw new BusinessException(ErrorCode.REPORT_INVALID_STATE);
        }
        this.status = ReportStatus.처리완료;
        this.adminId = adminId;
        this.adminMemo = memo;
        this.processedAt = now;
    }

    
    public void reject(Long adminId, String memo, LocalDateTime now) {
        validateAdminAndNow(adminId, now);
        if (status.isTerminal()) {
            throw new BusinessException(ErrorCode.REPORT_INVALID_STATE);
        }
        this.status = ReportStatus.반려;
        this.adminId = adminId;
        this.adminMemo = memo;
        this.processedAt = now;
    }

    private static void validateAdminAndNow(Long adminId, LocalDateTime now) {
        if (adminId == null || adminId <= 0) {
            throw new IllegalArgumentException("adminId 는 양수여야 합니다");
        }
        if (now == null) {
            throw new IllegalArgumentException("now 는 필수입니다");
        }
    }
}
