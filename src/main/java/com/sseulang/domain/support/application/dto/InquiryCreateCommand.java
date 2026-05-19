package com.sseulang.domain.support.application.dto;

import com.sseulang.domain.support.domain.InquiryCategory;

import java.util.List;

public record InquiryCreateCommand(
        InquiryCategory category,
        String title,
        String content,
        String email,
        List<String> imageUrls
) {}
