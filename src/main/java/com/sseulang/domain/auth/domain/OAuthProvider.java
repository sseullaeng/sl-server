package com.sseulang.domain.auth.domain;

import com.sseulang.domain.user.domain.SocialProvider;

/**
 * 소셜 로그인 provider 추상화. 도메인 layer 인터페이스 — Spring/HTTP 의존 X.
 * 각 provider 별 구현(KAKAO/GOOGLE)은 {@code domain/auth/infrastructure/oauth} 에 위치.
 *
 * <h2>예외 계약</h2>
 * <ul>
 *   <li>외부 API 호출 자체 실패 (network / 5xx / parse 등 인프라성) →
 *       {@link com.sseulang.global.exception.ExternalApiException} (가이드 §3.10 anti-corruption).
 *       호출자(ApplicationService 등)가 정책에 맞춰 BusinessException 으로 변환.</li>
 *   <li>토큰 무효 / 동의 항목 누락 / 검증 안 된 이메일 / 응답 비정상 →
 *       {@code BusinessException(ErrorCode.AUTH_OAUTH_FAILED)}.</li>
 * </ul>
 */
public interface OAuthProvider {

    SocialProvider supports();

    /**
     * Authorization Code Grant — code + redirectUri 로 provider token endpoint 호출,
     * access_token 교환 후 user info 조회. Client Secret 켜져있어도 안전.
     */
    OAuthUserInfo exchangeCodeAndFetch(String code, String redirectUri);
}
