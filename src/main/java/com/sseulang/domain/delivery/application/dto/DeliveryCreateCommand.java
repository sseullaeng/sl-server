package com.sseulang.domain.delivery.application.dto;

import java.time.LocalDateTime;

public record DeliveryCreateCommand(
        Long requesterId,
        String pickupAddress,
        String dropoffAddress,
        String itemDescription,
        long fee,
        LocalDateTime requestedDeadline,
        String memo
) {
    private static final int MAX_ADDRESS = 255;
    private static final int MAX_DESCRIPTION = 255;
    private static final int MAX_MEMO = 500;

    public DeliveryCreateCommand {
        pickupAddress = normalize(pickupAddress, "pickupAddress", MAX_ADDRESS);
        dropoffAddress = normalize(dropoffAddress, "dropoffAddress", MAX_ADDRESS);
        itemDescription = normalize(itemDescription, "itemDescription", MAX_DESCRIPTION);
        if (memo != null) {
            memo = memo.trim();
            if (memo.isEmpty()) memo = null;
            else if (memo.length() > MAX_MEMO) {
                throw new IllegalArgumentException("memo 는 " + MAX_MEMO + "자 이하여야 합니다");
            }
        }
    }

    private static String normalize(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 는 필수입니다");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(name + " 는 " + maxLength + "자 이하여야 합니다");
        }
        return trimmed;
    }
}
