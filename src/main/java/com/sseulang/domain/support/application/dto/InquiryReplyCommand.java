package com.sseulang.domain.support.application.dto;

import com.sseulang.domain.support.domain.InquiryStatus;

/**
 * 관리자 답변 Command. status 가 null 이면 기본 DONE 으로 자동 설정 (서비스에서 처리).
 *
 * <p>명세: 클라가 명시하면 그대로 따르고, status 안 보내면 DONE 으로 강제.</p>
 */
public record InquiryReplyCommand(
        String adminReply,
        InquiryStatus status
) {}
