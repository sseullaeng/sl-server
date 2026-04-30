package com.sseulang.domain.notice.presentation.dto;

import com.sseulang.domain.notice.application.dto.NoticeUpsertCommand;
import com.sseulang.domain.notice.domain.NoticeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record NoticeUpsertRequest(
        @NotNull NoticeType type,
        @NotBlank @Size(max = 200) String title,
        @NotBlank String content,
        @Size(max = 500) String imageUrl,
        LocalDateTime startsAt,
        LocalDateTime endsAt
) {
    public NoticeUpsertCommand toCommand() {
        return new NoticeUpsertCommand(type, title, content, imageUrl, startsAt, endsAt);
    }
}
