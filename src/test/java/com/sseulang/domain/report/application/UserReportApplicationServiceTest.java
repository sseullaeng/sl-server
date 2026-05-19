package com.sseulang.domain.report.application;

import com.sseulang.domain.report.domain.ReportStatus;
import com.sseulang.domain.report.domain.UserReport;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserReportApplicationServiceTest {

    private static final Long ADMIN = 99L;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 4, 30, 12, 0);

    private InMemoryFakeUserReportRepository repo;
    private UserReportApplicationService service;

    @BeforeEach
    void setUp() {
        repo = new InMemoryFakeUserReportRepository();
        Clock clock = Clock.fixed(NOW.atZone(KST).toInstant(), KST);
        service = new UserReportApplicationService(repo, org.mockito.Mockito.mock(com.sseulang.domain.user.application.UserApplicationService.class), clock);
    }

    @Test
    @DisplayName("adminMarkInProgress 정상_status=처리중")
    void markInProgress_정상() {
        Long id = service.reportUser(1L, 2L, "스팸", null);
        service.adminMarkInProgress(id, ADMIN, "검토 시작");

        UserReport r = service.adminFindById(id);
        assertThat(r.getStatus()).isEqualTo(ReportStatus.처리중);
        assertThat(r.getAdminId()).isEqualTo(ADMIN);
    }

    @Test
    @DisplayName("adminMarkInProgress 미존재_REPORT_NOT_FOUND")
    void markInProgress_미존재() {
        assertThatThrownBy(() -> service.adminMarkInProgress(999L, ADMIN, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REPORT_NOT_FOUND);
    }

    @Test
    @DisplayName("adminComplete 정상_처리중 → 처리완료")
    void complete_정상() {
        Long id = service.reportUser(1L, 2L, "스팸", null);
        service.adminMarkInProgress(id, ADMIN, null);
        service.adminComplete(id, ADMIN, "처리 완료");

        assertThat(service.adminFindById(id).getStatus()).isEqualTo(ReportStatus.처리완료);
    }

    @Test
    @DisplayName("adminReject 정상_접수에서 바로 반려")
    void reject_정상() {
        Long id = service.reportUser(1L, 2L, "스팸", null);
        service.adminReject(id, ADMIN, "근거 부족");

        assertThat(service.adminFindById(id).getStatus()).isEqualTo(ReportStatus.반려);
    }

    @Test
    @DisplayName("adminFindByStatus_status null = 전체, 필터 동작")
    void findByStatus_필터() {
        Long id1 = service.reportUser(1L, 2L, "스팸", null);
        Long id2 = service.reportUser(1L, 3L, "허위", null);
        service.adminReject(id2, ADMIN, null);

        Page<UserReport> 접수만 = service.adminFindByStatus(ReportStatus.접수, PageRequest.of(0, 10));
        assertThat(접수만.getContent()).extracting(UserReport::getId).containsExactly(id1);

        Page<UserReport> 전체 = service.adminFindByStatus(null, PageRequest.of(0, 10));
        assertThat(전체.getContent()).hasSize(2);
    }
}
