package com.sseulang.domain.auth.application;

import com.sseulang.domain.auth.application.dto.TokenPair;
import com.sseulang.domain.auth.domain.OAuthProvider;
import com.sseulang.domain.auth.domain.OAuthUserInfo;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.domain.user.domain.Email;
import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import com.sseulang.global.security.JwtClaims;
import com.sseulang.global.security.JwtProperties;
import com.sseulang.global.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthLoginServiceTest {

    private static final String SECRET = "test-secret-must-be-at-least-32-bytes-long-for-hs256-blah-blah";
    private static final long AT_VALIDITY = 1800L;
    private static final long RT_VALIDITY = 604800L;
    private static final Email EMAIL = new Email("user@kakao.com");
    private static final String PROVIDER_ID = "kakao-12345";
    private static final Long USER_ID = 7L;
    private static final OAuthUserInfo INFO =
            new OAuthUserInfo(SocialProvider.KAKAO, PROVIDER_ID, EMAIL, "쓸랭이", "https://img/u.png");

    private OAuthProvider kakaoProvider;
    private OAuthProvider googleProvider;
    private UserApplicationService userService;
    private JwtProvider jwtProvider;
    private InMemoryFakeRefreshTokenStore store;
    private OAuthLoginService service;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties(SECRET, AT_VALIDITY, RT_VALIDITY);
        Clock clock = Clock.fixed(Instant.parse("2026-04-28T03:00:00Z"), ZoneOffset.UTC);
        jwtProvider = new JwtProvider(props, clock);

        kakaoProvider = mock(OAuthProvider.class);
        when(kakaoProvider.supports()).thenReturn(SocialProvider.KAKAO);

        googleProvider = mock(OAuthProvider.class);
        when(googleProvider.supports()).thenReturn(SocialProvider.GOOGLE);

        userService = mock(UserApplicationService.class);
        store = new InMemoryFakeRefreshTokenStore();

        service = new OAuthLoginService(
                List.of(kakaoProvider, googleProvider),
                userService,
                jwtProvider,
                store,
                props
        );
    }

    private User userWithId(Long id, boolean blocked, boolean deleted) {
        User u = mock(User.class);
        when(u.getId()).thenReturn(id);
        when(u.isBlocked()).thenReturn(blocked);
        when(u.isDeleted()).thenReturn(deleted);
        return u;
    }

    @Test
    @DisplayName("login 정상_provider 호출 + user 생성/조회 + AT/RT 발급 + RT store 저장")
    void login_정상() {
        when(kakaoProvider.verifyAndFetch("KAKAO_TOKEN")).thenReturn(INFO);
        User u = userWithId(USER_ID, false, false);
        when(userService.findOrCreateBySocial(
                eq(SocialProvider.KAKAO), eq(PROVIDER_ID), eq(EMAIL), eq("쓸랭이"), eq("https://img/u.png")))
                .thenReturn(u);

        TokenPair pair = service.login(SocialProvider.KAKAO, "KAKAO_TOKEN");

        assertThat(pair.accessToken()).isNotBlank();
        assertThat(pair.refreshToken()).isNotBlank();

        // 발급된 AT 의 sub = USER_ID, role = USER
        JwtClaims atClaims = jwtProvider.parse(pair.accessToken());
        assertThat(atClaims.userId()).isEqualTo(USER_ID);
        assertThat(atClaims.role()).isEqualTo("USER");

        // RT jti 가 store 에 저장됨
        String rtJti = jwtProvider.parse(pair.refreshToken()).jti();
        assertThat(store.contains(USER_ID, rtJti)).isTrue();

        // 다른 provider 호출 X
        verify(googleProvider, never()).verifyAndFetch(any());
    }

    @Test
    @DisplayName("login_미지원 provider_AUTH_OAUTH_FAILED")
    void login_미지원provider() {
        // 본 service 에 KAKAO/GOOGLE 만 등록 → LOCAL 호출 시 실패
        assertThatThrownBy(() -> service.login(SocialProvider.LOCAL, "T"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_OAUTH_FAILED);

        verify(kakaoProvider, never()).verifyAndFetch(any());
        verify(googleProvider, never()).verifyAndFetch(any());
        verify(userService, never()).findOrCreateBySocial(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("login_provider 토큰 검증 실패_BusinessException 그대로 전파")
    void login_provider_검증실패() {
        when(kakaoProvider.verifyAndFetch("BAD")).thenThrow(new BusinessException(ErrorCode.AUTH_OAUTH_FAILED));

        assertThatThrownBy(() -> service.login(SocialProvider.KAKAO, "BAD"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_OAUTH_FAILED);

        verify(userService, never()).findOrCreateBySocial(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("login_외부 API 호출 실패(ExternalApiException)_AUTH_OAUTH_FAILED 변환 + user 조회 X")
    void login_externalApi_실패() {
        when(kakaoProvider.verifyAndFetch("T"))
                .thenThrow(new com.sseulang.global.exception.ExternalApiException(
                        "kakao-oauth", new RuntimeException("network")));

        assertThatThrownBy(() -> service.login(SocialProvider.KAKAO, "T"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_OAUTH_FAILED);

        verify(userService, never()).findOrCreateBySocial(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("login_blocked user_USER_BLOCKED + JWT 발급 X")
    void login_blocked() {
        when(kakaoProvider.verifyAndFetch("T")).thenReturn(INFO);
        User u = userWithId(USER_ID, true, false);
        when(userService.findOrCreateBySocial(any(), any(), any(), any(), any())).thenReturn(u);

        assertThatThrownBy(() -> service.login(SocialProvider.KAKAO, "T"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_BLOCKED);

        assertThat(store.size()).isZero();
    }

    @Test
    @DisplayName("login_deleted user_USER_BLOCKED")
    void login_deleted() {
        when(kakaoProvider.verifyAndFetch("T")).thenReturn(INFO);
        User u = userWithId(USER_ID, false, true);
        when(userService.findOrCreateBySocial(any(), any(), any(), any(), any())).thenReturn(u);

        assertThatThrownBy(() -> service.login(SocialProvider.KAKAO, "T"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_BLOCKED);
    }

    @Test
    @DisplayName("login_연속 호출_매번 새 jti store 저장 (누적 X 정책 X — Day3 단순화)")
    void login_연속() {
        when(kakaoProvider.verifyAndFetch(any())).thenReturn(INFO);
        User u = userWithId(USER_ID, false, false);
        when(userService.findOrCreateBySocial(any(), any(), any(), any(), any())).thenReturn(u);

        TokenPair p1 = service.login(SocialProvider.KAKAO, "T1");
        TokenPair p2 = service.login(SocialProvider.KAKAO, "T2");

        // 둘 다 store 에 등록 — 디바이스 동시 로그인 허용
        String j1 = jwtProvider.parse(p1.refreshToken()).jti();
        String j2 = jwtProvider.parse(p2.refreshToken()).jti();
        assertThat(j1).isNotEqualTo(j2);
        assertThat(store.contains(USER_ID, j1)).isTrue();
        assertThat(store.contains(USER_ID, j2)).isTrue();

        verify(userService, times(2)).findOrCreateBySocial(any(), any(), any(), any(), any());
    }
}
