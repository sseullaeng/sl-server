package com.sseulang.domain.overdue.domain.event;

import java.time.LocalDateTime;

public record OverdueAccountSuspendedEvent(Long overdueRecordId, Long buyerId, LocalDateTime occurredAt) {
}
