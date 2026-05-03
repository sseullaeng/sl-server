package com.sseulang.domain.user.application.dto;

import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
import com.sseulang.domain.user.domain.UserStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Admin 회원 카드용 enriched Result. tradeCount/reportCount 는 다른 도메인에서 batch fetch 후 채움.
 *
 * <p>{@code dormant}/{@code status} 는 User Aggregate 의 derive 결과 — DB 컬럼 X.</p>
 */
public record AdminUserResult(
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
        LocalDateTime lastLoginAt,
        LocalDateTime suspendedAt,
        Integer suspendDays,
        LocalDateTime suspendedUntil,
        boolean dormant,
        UserStatus status,
        long tradeCount,
        long reportCount,
        LocalDateTime createdAt
) {
    public static AdminUserResult from(
            User u,
            LocalDateTime now,
            int dormantThresholdDays,
            long tradeCount,
            long reportCount
    ) {
        LocalDateTime suspendedUntil = (u.getSuspendedAt() != null && u.getSuspendDays() != null && u.getSuspendDays() > 0)
                ? u.getSuspendedAt().plusDays(u.getSuspendDays())
                : null;
        return new AdminUserResult(
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
                u.getLastLoginAt(),
                u.getSuspendedAt(),
                u.getSuspendDays(),
                suspendedUntil,
                u.isDormantAt(now, dormantThresholdDays),
                u.derivedStatus(now, dormantThresholdDays),
                tradeCount,
                reportCount,
                u.getCreatedAt()
        );
    }
}
