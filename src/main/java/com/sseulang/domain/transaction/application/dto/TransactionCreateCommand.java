package com.sseulang.domain.transaction.application.dto;

import java.time.LocalDateTime;

public record TransactionCreateCommand(
        Long itemId,
        Long requesterId,
        Long chatRoomId,
        LocalDateTime rentalStart,
        LocalDateTime rentalEnd
) { }
