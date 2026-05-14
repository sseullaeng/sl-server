package com.sseulang.domain.overdue.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OverdueDetectionSchedulerTest {

    private OverdueApplicationService overdueService;
    private OverdueDetectionScheduler scheduler;
    private LocalDateTime fixedNow;

    @BeforeEach
    void setUp() {
        overdueService = mock(OverdueApplicationService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-05-15T02:00:00Z"), ZoneOffset.UTC);
        fixedNow = LocalDateTime.now(clock);
        scheduler = new OverdueDetectionScheduler(overdueService, clock);
    }

    @Test
    @DisplayName("run_신규감지_+_active_advance_둘다_호출")
    void run_invokes_both_phases() {
        when(overdueService.findOverdueCandidateEscrowIds(fixedNow.minusHours(24)))
                .thenReturn(List.of(100L, 101L));
        when(overdueService.findActiveRecordIds(200))
                .thenReturn(List.of(7L, 8L));
        when(overdueService.startOverdue(any(), any())).thenReturn(true);
        when(overdueService.advanceDay(any(), any())).thenReturn(true);

        scheduler.run();

        verify(overdueService).startOverdue(eq(100L), eq(fixedNow));
        verify(overdueService).startOverdue(eq(101L), eq(fixedNow));
        verify(overdueService).advanceDay(eq(7L), eq(fixedNow));
        verify(overdueService).advanceDay(eq(8L), eq(fixedNow));
    }

    @Test
    @DisplayName("run_startOverdue_예외나도_나머지_계속_처리")
    void run_continues_on_individual_failure() {
        when(overdueService.findOverdueCandidateEscrowIds(any()))
                .thenReturn(List.of(100L, 101L, 102L));
        when(overdueService.findActiveRecordIds(200)).thenReturn(List.of());
        when(overdueService.startOverdue(eq(100L), any())).thenReturn(true);
        when(overdueService.startOverdue(eq(101L), any())).thenThrow(new RuntimeException("boom"));
        when(overdueService.startOverdue(eq(102L), any())).thenReturn(true);

        scheduler.run();

        verify(overdueService).startOverdue(eq(100L), any());
        verify(overdueService).startOverdue(eq(101L), any());
        verify(overdueService).startOverdue(eq(102L), any());
    }

    @Test
    @DisplayName("run_빈_후보_빈_active_면_no-op")
    void run_no_targets() {
        when(overdueService.findOverdueCandidateEscrowIds(any())).thenReturn(List.of());
        when(overdueService.findActiveRecordIds(200)).thenReturn(List.of());

        scheduler.run();

        verify(overdueService, never()).startOverdue(any(), any());
        verify(overdueService, never()).advanceDay(any(), any());
        assertThat(true).isTrue();
    }
}
