package com.sseulang.domain.user.presentation.dto;

import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 본인 사용자 정보 응답 — 로그인/가입/oauth/refresh 응답 + GET /api/v1/users/me 의 표준 본문.
 *
 * <p>비밀번호 / social_id / address 등 민감 정보는 제외. 프론트가 헤더 사용자 영역, store 초기화에
 * 필요한 최소 필드만.</p>
 */
@Schema(description = "본인 사용자 정보 (헤더/스토어 초기화용).")
public record MeResponse(
        @Schema(example = "42") Long id,
        @Schema(example = "alice@sseulang.test") String email,
        @Schema(example = "쓸랭이") String nickname,
        @Schema(description = "프로필 이미지 URL (없으면 null)") String profileImage,
        @Schema(description = "LOCAL / KAKAO / GOOGLE") SocialProvider socialProvider,
        @Schema(example = "true", description = "이메일 인증 여부 — false 면 자금/거래 API 가 403") boolean emailVerified,
        @Schema(example = "50000", description = "포인트 잔액 (KRW)") long pointBalance,
        @Schema(example = "4.7", description = "리뷰 평균. 리뷰 0건이면 null") BigDecimal trustScore,
        @Schema(example = "12") int reviewCount
) {
    public static MeResponse from(User u) {
        return new MeResponse(
                u.getId(),
                u.getEmail(),
                u.getNickname(),
                u.getProfileImage(),
                u.getSocialProvider(),
                u.isEmailVerified(),
                u.getPointBalance(),
                u.getTrustScore(),
                u.getReviewCount()
        );
    }
}
