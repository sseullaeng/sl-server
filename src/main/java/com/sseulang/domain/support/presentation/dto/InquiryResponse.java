package com.sseulang.domain.support.presentation.dto;

import com.sseulang.domain.support.application.dto.InquiryResult;
import com.sseulang.domain.support.domain.InquiryCategory;
import com.sseulang.domain.support.domain.InquiryStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "1:1 문의 응답. 관리자 답변 미작성이면 adminReply / repliedAt 은 null.")
public record InquiryResponse(
        @Schema(example = "12") Long id,
        @Schema(example = "5") Long userId,
        InquiryCategory category,
        String title,
        String content,
        String email,
        InquiryStatus status,
        List<String> imageUrls,
        @Schema(nullable = true) String adminReply,
        @Schema(nullable = true) LocalDateTime repliedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static InquiryResponse from(InquiryResult r) {
        return new InquiryResponse(
                r.id(), r.userId(), r.category(), r.title(), r.content(), r.email(),
                r.status(), r.imageUrls(), r.adminReply(), r.repliedAt(),
                r.createdAt(), r.updatedAt()
        );
    }
}
