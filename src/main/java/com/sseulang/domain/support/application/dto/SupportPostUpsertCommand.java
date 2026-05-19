package com.sseulang.domain.support.application.dto;

import com.sseulang.domain.support.domain.InquiryCategory;
import com.sseulang.domain.support.domain.SupportPostType;

import java.util.List;

public record SupportPostUpsertCommand(
        SupportPostType postType,
        InquiryCategory category,
        String question,
        String answer,
        List<String> imageUrls
) {}
