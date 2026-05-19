package com.sseulang.domain.support.application.dto;

import com.sseulang.domain.support.domain.InquiryCategory;
import com.sseulang.domain.support.domain.SupportPost;
import com.sseulang.domain.support.domain.SupportPostType;

import java.time.LocalDateTime;
import java.util.List;

public record SupportPostResult(
        Long id,
        SupportPostType postType,
        InquiryCategory category,
        String question,
        String answer,
        List<String> imageUrls,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static SupportPostResult from(SupportPost p) {
        return new SupportPostResult(
                p.getId(),
                p.getPostType(),
                p.getCategory(),
                p.getQuestion(),
                p.getAnswer(),
                p.getImageUrls(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
