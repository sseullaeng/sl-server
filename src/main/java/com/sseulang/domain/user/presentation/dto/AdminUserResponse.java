package com.sseulang.domain.user.presentation.dto;

import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 관리자용 회원 단건 응답 — 비밀번호/소셜 토큰 등 민감 정보는 제외. */
public record AdminUserResponse(
        Long id,
        String email,
        String nickname,
        String profileImage,
        SocialProvider socialProvider,
        boolean blocked,
        boolean deleted,
        BigDecimal trustScore,
        int reviewCount,
        long pointBalance,
        LocalDateTime createdAt
) {
    public static AdminUserResponse from(User u) {
        return new AdminUserResponse(
                u.getId(),
                u.getEmail(),
                u.getNickname(),
                u.getProfileImage(),
                u.getSocialProvider(),
                u.isBlocked(),
                u.isDeleted(),
                u.getTrustScore(),
                u.getReviewCount(),
                u.getPointBalance(),
                u.getCreatedAt()
        );
    }
}
