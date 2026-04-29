package com.sseulang.domain.chat.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ChatRoomCreateRequest(@NotNull @Positive Long itemId) { }
