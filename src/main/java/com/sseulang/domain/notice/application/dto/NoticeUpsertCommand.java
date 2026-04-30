package com.sseulang.domain.notice.application.dto;

import com.sseulang.domain.notice.domain.NoticeType;

import java.time.LocalDateTime;

/**
 * 공지 생성·수정 Command. presentation 의 Request DTO 가 변환해 service 로 전달.
 * 검증은 {@link com.sseulang.domain.notice.domain.Notice} 정적 팩토리 / update 가 책임.
 */
public record NoticeUpsertCommand(
        NoticeType type,
        String title,
        String content,
        String imageUrl,
        LocalDateTime startsAt,
        LocalDateTime endsAt
) {}
