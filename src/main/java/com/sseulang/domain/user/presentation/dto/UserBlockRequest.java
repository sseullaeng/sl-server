package com.sseulang.domain.user.presentation.dto;

import jakarta.validation.constraints.NotNull;

public record UserBlockRequest(@NotNull Boolean blocked) {}
