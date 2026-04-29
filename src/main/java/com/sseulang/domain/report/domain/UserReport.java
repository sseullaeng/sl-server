package com.sseulang.domain.report.domain;

import com.sseulang.global.common.BaseEntity;
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

/**
 * 사용자/물품 신고 Aggregate. {@code user_reports} 테이블 — CHECK reported_id XOR item_id (둘 중 하나는 NOT NULL).
 * 관리자 처리 흐름(상태 전이)은 Day 9 admin 도메인.
 */
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
}
