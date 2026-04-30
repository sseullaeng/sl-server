package com.sseulang.domain.notice.presentation.dto;

import jakarta.validation.constraints.NotNull;

/** pin / publish 토글 공용 — 단순 boolean payload. */
public record NoticeToggleRequest(@NotNull Boolean value) {}
