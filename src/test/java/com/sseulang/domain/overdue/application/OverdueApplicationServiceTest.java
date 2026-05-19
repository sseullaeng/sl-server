package com.sseulang.domain.overdue.application;

import com.sseulang.domain.escrow.application.EscrowOverdueQueryService;
import com.sseulang.domain.escrow.application.dto.EscrowOverdueSnapshot;
import com.sseulang.domain.escrow.domain.EscrowApplicationStatus;
import com.sseulang.domain.notification.application.NotificationApplicationService;
import com.sseulang.domain.notification.domain.NotificationType;
import com.sseulang.domain.overdue.domain.OverduePhase;
import com.sseulang.domain.overdue.domain.OverdueRecord;
import com.sseulang.domain.overdue.domain.OverdueStatus;
import com.sseulang.domain.point.application.PointApplicationService;
import com.sseulang.domain.point.domain.PointHistoryType;
import com.sseulang.domain.point.domain.PointReferenceType;
import com.sseulang.domain.user.application.UserApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OverdueApplicationServiceTest {

    private static final LocalDateTime RENTAL_END = LocalDateTime.of(2026, 5, 1, 10, 0);
    private static final LocalDateTime STARTED = RENTAL_END.plusHours(24);

    private InMemoryFakeOverdueRecordRepository overdueRepo;
    private EscrowOverdueQueryService escrowQueryService;
    private UserApplicationService userService;
    private PointApplicationService pointService;
    private NotificationApplicationService notificationService;
    private OverdueApplicationService service;

    @BeforeEach
    void setUp() {
        overdueRepo = new InMemoryFakeOverdueRecordRepository();
        escrowQueryService = mock(EscrowOverdueQueryService.class);
        userService = mock(UserApplicationService.class);
        pointService = mock(PointApplicationService.class);
        notificationService = mock(NotificationApplicationService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-05-02T10:00:00Z"), ZoneOffset.UTC);
        service = new OverdueApplicationService(
                overdueRepo,
                escrowQueryService,
                userService,
                pointService,
                notificationService,
                clock,
                50_000,
                14,
                7,
                20
        );
    }

    @Test
    @DisplayName("startOverdue_신규_record_생성하고_1일차_보증금몰수_처리")
    void startOverdue_creates_record_and_forfeits_day_1() {
        when(escrowQueryService.getForOverdue(100L)).thenReturn(snapshot(100_000L));

        boolean started = service.startOverdue(100L, STARTED);

        assertThat(started).isTrue();
        OverdueRecord record = overdueRepo.all().get(0);
        assertThat(record.getEscrowApplicationId()).isEqualTo(100L);
        assertThat(record.getOverdueDays()).isEqualTo(1);
        assertThat(record.getDepositForfeitedAmount()).isEqualTo(30_000L);
        verify(userService).releaseHold(11L, 30_000L);
        verify(pointService).credit(
                eq(20L), eq(30_000L),
                eq(PointHistoryType.연체몰수), eq(PointReferenceType.OVERDUE), eq(record.getId()),
                contains("1일차")
        );
    }

    @Test
    @DisplayName("startOverdue_중복_record_있으면_noop")
    void startOverdue_duplicate_noop() {
        overdueRepo.save(OverdueRecord.create(100L, 11L, 20L, 100_000L, RENTAL_END, STARTED));

        boolean started = service.startOverdue(100L, STARTED);

        assertThat(started).isFalse();
        verifyNoInteractions(escrowQueryService, userService, pointService);
    }

    @Test
    @DisplayName("advanceDay_8일차_증분_몰수와_추가채무_처리_+_phase3_임계_초과시_자동정지")
    void advanceDay_applies_delta_only() {
        when(escrowQueryService.getForOverdue(100L)).thenReturn(snapshot(100_000L));
        service.startOverdue(100L, STARTED);
        OverdueRecord record = overdueRepo.all().get(0);
        clearInvocations(userService, pointService);

        boolean advanced = service.advanceDay(record.getId(), STARTED.plusDays(7));

        assertThat(advanced).isTrue();
        assertThat(record.getOverdueDays()).isEqualTo(8);
        // Day 8 totalDebt = 90k(몰수) + 20k(추가채무) = 110k ≥ phase3AmountKrw(50k) → 즉시 PHASE_3 트리거
        assertThat(record.getPhase()).isEqualTo(OverduePhase.PHASE_3);
        assertThat(record.getAccountSuspendedAt()).isNotNull();
        assertThat(record.getDepositForfeitedAmount()).isEqualTo(90_000L);
        assertThat(record.getExtraDebtAmount()).isEqualTo(20_000L);
        verify(userService).releaseHold(11L, 60_000L);
        verify(pointService).credit(
                eq(20L), eq(60_000L),
                eq(PointHistoryType.연체몰수), eq(PointReferenceType.OVERDUE), eq(record.getId()),
                contains("8일차")
        );
        verify(userService).incrementOverdueDebt(11L, 20_000L);
        verify(userService).adminAutoSuspend(eq(11L), anyInt(), contains("연체 임계값"));
    }

    @Test
    @DisplayName("advanceDay_같은날_재실행은_noop")
    void advanceDay_same_day_noop() {
        when(escrowQueryService.getForOverdue(100L)).thenReturn(snapshot(100_000L));
        service.startOverdue(100L, STARTED);
        OverdueRecord record = overdueRepo.all().get(0);
        clearInvocations(userService, pointService);

        boolean advanced = service.advanceDay(record.getId(), STARTED);

        assertThat(advanced).isFalse();
        verifyNoInteractions(userService, pointService);
    }

    @Test
    @DisplayName("markResolvedByReturn_연체record_없으면_false")
    void markResolvedByReturn_absent_false() {
        assertThat(service.markResolvedByReturn(100L, STARTED)).isFalse();
    }

    @Test
    @DisplayName("markResolvedByReturn_잔여보증금만_환불하고_추가채무_있으면_정산완료")
    void markResolvedByReturn_refunds_remaining_deposit() {
        when(escrowQueryService.getForOverdue(100L)).thenReturn(snapshot(100_000L));
        service.startOverdue(100L, STARTED);
        OverdueRecord record = overdueRepo.all().get(0);
        service.advanceDay(record.getId(), STARTED.plusDays(7));
        clearInvocations(userService, pointService);

        boolean handled = service.markResolvedByReturn(100L, STARTED.plusDays(8));

        assertThat(handled).isTrue();
        assertThat(record.getStatus()).isEqualTo(OverdueStatus.정산완료);
        verify(userService).refundHold(11L, 10_000L);
        verifyNoInteractions(pointService);
    }

    @Test
    @DisplayName("findOverdueCandidateEscrowIds_이미_record_있는_escrow는_제외")
    void findOverdueCandidateEscrowIds_excludes_existing_records() {
        overdueRepo.save(OverdueRecord.create(100L, 11L, 20L, 100_000L, RENTAL_END, STARTED));
        when(escrowQueryService.findOverdueCandidates(STARTED)).thenReturn(List.of(
                snapshot(100L, 100_000L),
                snapshot(101L, 50_000L)
        ));

        List<Long> ids = service.findOverdueCandidateEscrowIds(STARTED);

        assertThat(ids).containsExactly(101L);
    }

    @Test
    @DisplayName("startOverdue_Day1_buyer_알림_발송")
    void startOverdue_sends_day1_notification() {
        when(escrowQueryService.getForOverdue(100L)).thenReturn(snapshot(100_000L));

        service.startOverdue(100L, STARTED);

        verify(notificationService).notify(
                eq(11L),
                eq(NotificationType.시스템),
                contains("반납 기한 초과"),
                anyString(),
                eq("OVERDUE"),
                anyLong()
        );
    }

    @Test
    @DisplayName("advanceDay_Phase1_to_Phase2_전이시_알림_발송")
    void advanceDay_phase2_transition_notifies() {
        when(escrowQueryService.getForOverdue(100L)).thenReturn(snapshot(100_000L));
        service.startOverdue(100L, STARTED);
        OverdueRecord record = overdueRepo.all().get(0);
        clearInvocations(notificationService);

        service.advanceDay(record.getId(), STARTED.plusDays(7));

        verify(notificationService).notify(
                eq(11L),
                eq(NotificationType.시스템),
                contains("보증금 초과 채무"),
                anyString(),
                eq("OVERDUE"),
                eq(record.getId())
        );
    }

    @Test
    @DisplayName("advanceDay_phase3_threshold_도달시_자동정지_+_알림")
    void advanceDay_phase3_auto_suspends() {
        when(escrowQueryService.getForOverdue(100L)).thenReturn(snapshot(100_000L));
        service.startOverdue(100L, STARTED);
        OverdueRecord record = overdueRepo.all().get(0);
        clearInvocations(userService, notificationService);

        // Day 14 도달 — phase3DaysThreshold=14 트리거
        service.advanceDay(record.getId(), STARTED.plusDays(13));

        assertThat(record.getOverdueDays()).isEqualTo(14);
        assertThat(record.getAccountSuspendedAt()).isNotNull();
        assertThat(record.getPhase()).isEqualTo(OverduePhase.PHASE_3);

        verify(userService).adminAutoSuspend(eq(11L), eq(30), contains("연체 임계값"));
        verify(notificationService).notify(
                eq(11L),
                eq(NotificationType.시스템),
                contains("계정 정지"),
                anyString(),
                eq("OVERDUE"),
                eq(record.getId())
        );
    }

    @Test
    @DisplayName("advanceDay_이미_정지된_record는_재정지_안함_(idempotent)")
    void advanceDay_already_suspended_no_resuspend() {
        when(escrowQueryService.getForOverdue(100L)).thenReturn(snapshot(100_000L));
        service.startOverdue(100L, STARTED);
        OverdueRecord record = overdueRepo.all().get(0);
        service.advanceDay(record.getId(), STARTED.plusDays(13));  // Day 14 → suspend
        clearInvocations(userService, notificationService);

        service.advanceDay(record.getId(), STARTED.plusDays(14));  // Day 15

        verify(userService, never()).adminAutoSuspend(anyLong(), anyInt(), anyString());
    }

    @Test
    @DisplayName("markResolvedByReturn_buyer_정산완료_알림_발송")
    void markResolvedByReturn_sends_settlement_notification() {
        when(escrowQueryService.getForOverdue(100L)).thenReturn(snapshot(100_000L));
        service.startOverdue(100L, STARTED);
        OverdueRecord record = overdueRepo.all().get(0);
        clearInvocations(notificationService);

        service.markResolvedByReturn(100L, STARTED.plusDays(1));

        verify(notificationService).notify(
                eq(11L),
                eq(NotificationType.시스템),
                contains("정산완료"),
                anyString(),
                eq("OVERDUE"),
                eq(record.getId())
        );
    }

    private static EscrowOverdueSnapshot snapshot(long depositAmount) {
        return snapshot(100L, depositAmount);
    }

    private static EscrowOverdueSnapshot snapshot(Long id, long depositAmount) {
        return new EscrowOverdueSnapshot(
                id,
                11L,
                20L,
                depositAmount,
                RENTAL_END,
                EscrowApplicationStatus.사용중,
                true
        );
    }
}
