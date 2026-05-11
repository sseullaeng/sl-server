package com.sseulang.domain.user.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사용자 가입 출처. LOCAL(이메일/비밀번호) / KAKAO / GOOGLE / DEV(로컬 전용).")
public enum SocialProvider {
    LOCAL,
    KAKAO,
    GOOGLE,
    DEV;
}
