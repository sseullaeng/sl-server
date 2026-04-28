package com.sseulang.global.security;

import com.sseulang.domain.auth.domain.AccessTokenBlacklist;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-must-be-at-least-32-bytes-long-for-hs256-blah-blah";
    private static final long AT_VALIDITY = 1800L;
    private static final long RT_VALIDITY = 604800L;
    private static final Instant FIXED_NOW = Instant.parse("2026-04-28T03:00:00Z");

    @Mock
    private HandlerExceptionResolver resolver;

    @Mock
    private AccessTokenBlacklist blacklist;

    private JwtProvider jwtProvider;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties(SECRET, AT_VALIDITY, RT_VALIDITY);
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        jwtProvider = new JwtProvider(props, fixedClock);
        filter = new JwtAuthenticationFilter(jwtProvider, blacklist, resolver);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("쿠키 없음_인증 시도 X_체인 통과")
    void 쿠키없음_체인통과() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isSameAs(req);  // chain 호출됨
        verify(resolver, never()).resolveException(any(), any(), any(), any());
    }

    @Test
    @DisplayName("유효한 AT 쿠키_SecurityContext 에 Authentication 주입")
    void 유효AT_인증주입() throws Exception {
        String token = jwtProvider.issueAccessToken(42L, "USER");
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(new Cookie(CookieUtil.ACCESS_TOKEN_COOKIE, token));
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(42L);
        assertThat(auth.getAuthorities()).hasSize(1);
        assertThat(auth.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_USER");
        verify(resolver, never()).resolveException(any(), any(), any(), any());
    }

    @Test
    @DisplayName("만료 AT_resolver 위임_에러코드 EXPIRED")
    void 만료AT_resolver위임() throws Exception {
        String token = jwtProvider.issueAccessToken(1L, "USER");
        // 31분 뒤 시점의 필터로 검증
        Clock laterClock = Clock.fixed(FIXED_NOW.plusSeconds(AT_VALIDITY + 1), ZoneOffset.UTC);
        JwtProvider laterProvider = new JwtProvider(
                new JwtProperties(SECRET, AT_VALIDITY, RT_VALIDITY), laterClock);
        JwtAuthenticationFilter laterFilter = new JwtAuthenticationFilter(laterProvider, blacklist, resolver);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(new Cookie(CookieUtil.ACCESS_TOKEN_COOKIE, token));
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        laterFilter.doFilter(req, res, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(resolver, times(1)).resolveException(eq(req), eq(res), any(),
                org.mockito.ArgumentMatchers.argThat(t ->
                        t instanceof BusinessException be
                                && be.getErrorCode() == ErrorCode.AUTH_TOKEN_EXPIRED));
        // chain 은 호출되면 안 됨
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("변조 AT_resolver 위임_에러코드 INVALID")
    void 변조AT_resolver위임() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(new Cookie(CookieUtil.ACCESS_TOKEN_COOKIE, "tampered.token.value"));
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(resolver, times(1)).resolveException(eq(req), eq(res), any(),
                org.mockito.ArgumentMatchers.argThat(t ->
                        t instanceof BusinessException be
                                && be.getErrorCode() == ErrorCode.AUTH_TOKEN_INVALID));
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("다른 이름의 쿠키만 있음_AT 무시_anonymous 통과")
    void 다른쿠키만_AT무시() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(new Cookie("session_id", "abc"), new Cookie("xsrf", "csrf"));
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isSameAs(req);
        verify(resolver, never()).resolveException(any(), any(), any(), any());
    }

    @Test
    @DisplayName("블랙리스트 등록된 AT_AUTH_TOKEN_REVOKED 위임")
    void 블랙리스트AT_REVOKED() throws Exception {
        String token = jwtProvider.issueAccessToken(42L, "USER");
        String jti = jwtProvider.parse(token).jti();
        org.mockito.Mockito.when(blacklist.isBlacklisted(jti)).thenReturn(true);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(new Cookie(CookieUtil.ACCESS_TOKEN_COOKIE, token));
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(resolver, times(1)).resolveException(eq(req), eq(res), any(),
                org.mockito.ArgumentMatchers.argThat(t ->
                        t instanceof BusinessException be
                                && be.getErrorCode() == ErrorCode.AUTH_TOKEN_REVOKED));
        assertThat(chain.getRequest()).isNull();
    }
}
