package com.sseulang.domain.overdue.domain.event;

import com.sseulang.domain.overdue.domain.OverduePhase;

import java.time.LocalDateTime;

public record OverduePhaseChangedEvent(Long overdueRecordId, OverduePhase phase, LocalDateTime occurredAt) {
}
