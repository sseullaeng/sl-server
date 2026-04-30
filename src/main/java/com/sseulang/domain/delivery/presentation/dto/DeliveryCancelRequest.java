package com.sseulang.domain.delivery.presentation.dto;

import jakarta.validation.constraints.Size;

public record DeliveryCancelRequest(@Size(max = 255) String reason) {
}
