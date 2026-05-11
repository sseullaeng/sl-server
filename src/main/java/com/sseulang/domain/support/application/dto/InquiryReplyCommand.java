package com.sseulang.domain.support.application.dto;

import com.sseulang.domain.support.domain.InquiryStatus;

public record InquiryReplyCommand(
        String adminReply,
        InquiryStatus status
) {}
