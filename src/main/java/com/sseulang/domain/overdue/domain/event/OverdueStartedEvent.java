package com.sseulang.domain.overdue.domain.event;

import java.time.LocalDateTime;

public record OverdueStartedEvent(Long overdueRecordId, Long escrowApplicationId, Long buyerId, LocalDateTime occurredAt) {
}
