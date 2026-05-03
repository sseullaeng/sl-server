package com.sseulang.domain.support.application.dto;

import com.sseulang.domain.support.domain.InquiryCategory;

import java.util.List;

/** 1:1 문의 작성 Command. 검증 일부는 Inquiry.create 위임. */
public record InquiryCreateCommand(
        InquiryCategory category,
        String title,
        String content,
        String email,
        List<String> imageUrls
) {}
