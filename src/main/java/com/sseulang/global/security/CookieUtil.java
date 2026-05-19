package com.sseulang.global.security;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 인증 쿠키(AT/RT) 빌더. 모두 HttpOnly. secure / sameSite / domain 은 yml(app.cookie) 주입.
 *
 * Path 분리:
 *  - AT: "/" — 모든 API 에 동봉
 *  - RT: "/api/v1/auth" — refresh / logout 엔드포인트 한정 (CSRF 표면적 최소화)
 */
@Component
public class CookieUtil {

    public static final String ACCESS_TOKEN_COOKIE = "at";
    public static final String REFRESH_TOKEN_COOKIE = "rt";
    public static final String ACCESS_TOKEN_PATH = "/";
    public static final String REFRESH_TOKEN_PATH = "/api/v1/auth";

    private final CookieProperties cookieProps;
    private final long accessTokenMaxAge;
    private final long refreshTokenMaxAge;

    public CookieUtil(CookieProperties cookieProps, JwtProperties jwtProps) {
        this.cookieProps = cookieProps;
        this.accessTokenMaxAge = jwtProps.accessTokenValiditySeconds();
        this.refreshTokenMaxAge = jwtProps.refreshTokenValiditySeconds();
    }

    public ResponseCookie accessTokenCookie(String token) {
        return baseBuilder(ACCESS_TOKEN_COOKIE, token, ACCESS_TOKEN_PATH, accessTokenMaxAge).build();
    }

    public ResponseCookie refreshTokenCookie(String token) {
        return baseBuilder(REFRESH_TOKEN_COOKIE, token, REFRESH_TOKEN_PATH, refreshTokenMaxAge).build();
    }

    public ResponseCookie deleteAccessTokenCookie() {
        return baseBuilder(ACCESS_TOKEN_COOKIE, "", ACCESS_TOKEN_PATH, 0L).build();
    }

    public ResponseCookie deleteRefreshTokenCookie() {
        return baseBuilder(REFRESH_TOKEN_COOKIE, "", REFRESH_TOKEN_PATH, 0L).build();
    }

    private ResponseCookie.ResponseCookieBuilder baseBuilder(String name, String value, String path, long maxAgeSeconds) {
        ResponseCookie.ResponseCookieBuilder b = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cookieProps.secure())
                .sameSite(cookieProps.sameSite())
                .path(path)
                .maxAge(maxAgeSeconds);
        if (cookieProps.domain() != null && !cookieProps.domain().isBlank()) {
            b = b.domain(cookieProps.domain());
        }
        return b;
    }
}
