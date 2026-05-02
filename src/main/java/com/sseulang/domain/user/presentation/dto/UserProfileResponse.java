package com.sseulang.domain.user.presentation.dto;

import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 다른 사용자(판매자/구매자/리뷰어 등) 의 공개 프로필 — {@code GET /api/v1/users/{id}/profile} 응답.
 *
 * <p>이메일 / 잔액 / emailVerified 등 민감 정보는 노출 X. ItemDetail 화면에서 sellerId 로 호출해
 * 카드 렌더링하는 용도. 비로그인도 접근 가능 (공개).</p>
 */
@Schema(description = "다른 사용자의 공개 프로필 — 닉네임 + 신뢰도 정보. 비로그인 접근 가능.")
public record UserProfileResponse(
        @Schema(example = "100") Long id,
        @Schema(example = "쓸랭이") String nickname,
        @Schema(description = "프로필 이미지 URL (없으면 null)") String profileImage,
        @Schema(description = "LOCAL / KAKAO / GOOGLE") SocialProvider socialProvider,
        @Schema(example = "4.7", description = "리뷰 평균. 리뷰 0건이면 null (신규 사용자)") BigDecimal trustScore,
        @Schema(example = "12") int reviewCount,
        LocalDateTime createdAt
) {
    public static UserProfileResponse from(User u) {
        return new UserProfileResponse(
                u.getId(),
                u.getNickname(),
                u.getProfileImage(),
                u.getSocialProvider(),
                u.getTrustScore(),
                u.getReviewCount(),
                u.getCreatedAt()
        );
    }
}
