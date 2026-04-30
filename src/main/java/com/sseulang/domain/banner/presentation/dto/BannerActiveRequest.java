package com.sseulang.domain.banner.presentation.dto;

import jakarta.validation.constraints.NotNull;

public record BannerActiveRequest(@NotNull Boolean active) {}
