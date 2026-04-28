package com.sseulang.domain.user.domain;

/**
 * 사용자 가입 출처. V1 스키마의 {@code users.social_provider} 컬럼과 1:1 매핑.
 * <ul>
 *   <li>{@link #LOCAL} — 이메일/비밀번호 가입 (5/6 이후 영역)</li>
 *   <li>{@link #KAKAO} / {@link #GOOGLE} — 소셜 가입 (Day 3 본 PR 범위)</li>
 * </ul>
 */
public enum SocialProvider {
    LOCAL,
    KAKAO,
    GOOGLE;
}
