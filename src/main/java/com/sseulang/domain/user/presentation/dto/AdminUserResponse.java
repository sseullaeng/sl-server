package com.sseulang.domain.user.presentation.dto;

import com.sseulang.domain.user.application.dto.AdminUserResult;
import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
import com.sseulang.domain.user.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
        @Schema(description = "마지막 로그인 시각. null 이면 가입 후 미로그인.", nullable = true) LocalDateTime lastLoginAt,
        @Schema(description = "시한부 활동정지 시작 시각. is_blocked(영구) 와 별개.", nullable = true) LocalDateTime suspendedAt,
        @Schema(description = "활동정지 기간(일).", nullable = true) Integer suspendDays,
        @Schema(description = "활동정지 만료 시각 (suspendedAt + suspendDays).", nullable = true) LocalDateTime suspendedUntil,
        @Schema(example = "false", description = "휴면 여부 (lastLoginAt 90일 이상 미접속)") boolean dormant,
        @Schema(description = "회원 상태 (derived). WITHDRAWN > SUSPENDED > DORMANT > ACTIVE 우선순위.") UserStatus status,
        @Schema(example = "5", description = "이 회원이 buyer 또는 seller 로 참여한 거래 횟수") long tradeCount,
        @Schema(example = "0", description = "이 회원이 신고당한 횟수") long reportCount,
        @Schema(example = "0", description = "현재 누적 연체 채무 (원). 0 이면 채무 없음.") long overdueDebt,
        @Schema(description = "진행중/법적조치중 연체 record id. 없으면 null.", nullable = true) Long activeOverdueRecordId,
        LocalDateTime createdAt
) {
    public static AdminUserResponse from(AdminUserResult r) {
        return new AdminUserResponse(
                r.id(), r.email(), r.nickname(), r.profileImage(), r.socialProvider(),
                r.blocked(), r.deleted(), r.trustScore(), r.reviewCount(), r.pointBalance(),
                r.lastLoginAt(), r.suspendedAt(), r.suspendDays(), r.suspendedUntil(),
                r.dormant(), r.status(),
                r.tradeCount(), r.reportCount(),
                r.overdueDebt(), r.activeOverdueRecordId(),
                r.createdAt()
        );
    }



    @Deprecated
    public static AdminUserResponse from(User u) {
        return new AdminUserResponse(
                u.getId(), u.getEmail(), u.getNickname(), u.getProfileImage(), u.getSocialProvider(),
                u.isBlocked(), u.isDeleted(), u.getTrustScore(), u.getReviewCount(), u.getPointBalance(),
                u.getLastLoginAt(), u.getSuspendedAt(), u.getSuspendDays(), null,
                false, UserStatus.ACTIVE,
                0L, 0L,
                0L, null,
                u.getCreatedAt()
        );
    }
}
