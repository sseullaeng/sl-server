package com.sseulang.domain.auth.domain;

import com.sseulang.domain.user.domain.SocialProvider;

/**
 * 소셜 로그인 provider 추상화. 도메인 layer 인터페이스 — Spring/HTTP 의존 X.
 * 각 provider 별 구현(KAKAO/GOOGLE)은 {@code domain/auth/infrastructure/oauth} 에 위치.
 *
 * <p>토큰 검증 실패 / 네트워크 오류 / 응답 파싱 실패 모두
 * {@code BusinessException(AUTH_OAUTH_FAILED)} 으로 통일.</p>
 */
public interface OAuthProvider {

    SocialProvider supports();

    OAuthUserInfo verifyAndFetch(String accessToken);
}
