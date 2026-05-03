package com.sseulang.domain.support.application.dto;

import com.sseulang.domain.support.domain.InquiryCategory;
import com.sseulang.domain.support.domain.SupportPostType;

import java.util.List;

/** FAQ/QNA 게시글 생성·수정 Command. 검증은 SupportPost 도메인 위임. */
public record SupportPostUpsertCommand(
        SupportPostType postType,
        InquiryCategory category,
        String question,
        String answer,
        List<String> imageUrls
) {}
