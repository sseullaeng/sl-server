package com.sseulang.domain.block.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UserBlockRequest(@NotNull @Positive Long userId) { }
