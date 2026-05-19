package com.sseulang.domain.notice.presentation.dto;

import com.sseulang.domain.notice.application.dto.NoticeResult;
import com.sseulang.domain.notice.domain.NoticeType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "공지사항 — 관리자 작성. pinned 는 상단 고정, published+startsAt~endsAt 로 노출 제어.")
public record NoticeResponse(
        @Schema(example = "3") Long id,
        @Schema(example = "1", description = "작성한 관리자 id") Long adminId,
        NoticeType type,
        @Schema(example = "결제 시스템 점검 안내") String title,
        @Schema(example = "5/3 03:00~05:00 결제 일시 중단됩니다.") String content,
        @Schema(description = "본문 상단 이미지 (선택)") String imageUrl,
        @Schema(example = "true", description = "상단 고정 여부") boolean pinned,
        @Schema(example = "true", description = "게시 여부") boolean published,
        @Schema(example = "152") int viewCount,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        LocalDateTime createdAt
) {
    public static NoticeResponse from(NoticeResult r) {
        return new NoticeResponse(
                r.id(), r.adminId(), r.type(), r.title(), r.content(), r.imageUrl(),
                r.pinned(), r.published(), r.viewCount(), r.startsAt(), r.endsAt(), r.createdAt()
        );
    }
}
