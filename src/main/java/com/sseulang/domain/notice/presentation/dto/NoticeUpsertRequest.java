package com.sseulang.domain.notice.presentation.dto;

import com.sseulang.domain.notice.application.dto.NoticeUpsertCommand;
import com.sseulang.domain.notice.domain.NoticeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Schema(description = "공지/이벤트 등록·수정 (관리자).")
public record NoticeUpsertRequest(
        @Schema(description = "공지 유형", example = "공지", allowableValues = {"공지", "이벤트"})
        @NotNull NoticeType type,

        @Schema(description = "제목", example = "5월 거래 수수료 무료 이벤트", maxLength = 200)
        @NotBlank @Size(max = 200) String title,

        @Schema(description = "본문 (HTML/Markdown 자유)", example = "5월 한 달간 모든 거래 수수료가 무료입니다.")
        @NotBlank String content,

        @Schema(description = "썸네일 이미지 URL (선택)", example = "https://cdn.sseulang.test/notices/202605.jpg", maxLength = 500, nullable = true)
        @Size(max = 500) String imageUrl,

        @Schema(description = "노출 시작 시각", example = "2026-05-01T00:00:00", nullable = true)
        LocalDateTime startsAt,

        @Schema(description = "노출 종료 시각", example = "2026-05-31T23:59:59", nullable = true)
        LocalDateTime endsAt
) {
    public NoticeUpsertCommand toCommand() {
        return new NoticeUpsertCommand(type, title, content, imageUrl, startsAt, endsAt);
    }
}
