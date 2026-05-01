package com.sseulang.domain.user.presentation.dto;

import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 관리자용 회원 단건 응답 — 비밀번호/소셜 토큰 등 민감 정보는 제외. */
@Schema(description = "관리자 — 회원 단건 (민감 정보 제외).")
public record AdminUserResponse(
        @Schema(example = "100") Long id,
        @Schema(example = "user@example.com") String email,
        @Schema(example = "쓸랭이") String nickname,
        @Schema(example = "https://cdn.sseulang.com/profile/100/avatar.jpg") String profileImage,
        @Schema(description = "LOCAL / KAKAO / GOOGLE") SocialProvider socialProvider,
        @Schema(example = "false") boolean blocked,
        @Schema(example = "false") boolean deleted,
        @Schema(example = "4.7", description = "받은 리뷰 평균 (1.0~5.0). 리뷰 0건이면 null") BigDecimal trustScore,
        @Schema(example = "12") int reviewCount,
        @Schema(example = "50000", description = "현재 포인트 잔액 (KRW)") long pointBalance,
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
