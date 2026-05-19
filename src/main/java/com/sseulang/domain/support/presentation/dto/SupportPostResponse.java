package com.sseulang.domain.support.presentation.dto;

import com.sseulang.domain.support.application.dto.SupportPostResult;
import com.sseulang.domain.support.domain.InquiryCategory;
import com.sseulang.domain.support.domain.SupportPostType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "FAQ / QNA 응답.")
public record SupportPostResponse(
        Long id,
        SupportPostType postType,
        InquiryCategory category,
        String question,
        String answer,
        List<String> imageUrls,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static SupportPostResponse from(SupportPostResult r) {
        return new SupportPostResponse(
                r.id(), r.postType(), r.category(), r.question(), r.answer(),
                r.imageUrls(), r.createdAt(), r.updatedAt()
        );
    }
}
