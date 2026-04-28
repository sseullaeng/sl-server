package com.sseulang.domain.auth.domain;

import com.sseulang.domain.user.domain.Email;
import com.sseulang.domain.user.domain.SocialProvider;

/**
 * Provider API 가 돌려준 사용자 정보의 도메인 표현. profileImage 는 nullable.
 */
public record OAuthUserInfo(
        SocialProvider provider,
        String providerId,
        Email email,
        String nickname,
        String profileImage
) {
    public OAuthUserInfo {
        if (provider == null || provider == SocialProvider.LOCAL) {
            throw new IllegalArgumentException("provider 는 KAKAO/GOOGLE 만 허용");
        }
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId 는 필수");
        }
        if (email == null) {
            throw new IllegalArgumentException("email 은 필수");
        }
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("nickname 은 필수");
        }
    }
}
