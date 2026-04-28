package com.sseulang.global.security;

import com.sseulang.domain.auth.domain.AccessTokenBlacklist;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.List;

/**
 * AT 쿠키에서 토큰 추출 → 검증 → SecurityContext 주입.
 *
 * 쿠키가 없으면 anonymous 로 통과 (PUBLIC 엔드포인트 정상, USER 엔드포인트는 SecurityConfig 가 401).
 * 쿠키가 있는데 invalid/expired 면 BusinessException 을 GlobalExceptionHandler 로 위임 →
 * 정확한 에러 코드(AUTH_TOKEN_EXPIRED / INVALID) 로 ApiResponse 401 응답.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final AccessTokenBlacklist accessTokenBlacklist;
    private final HandlerExceptionResolver resolver;

    public JwtAuthenticationFilter(
            JwtProvider jwtProvider,
            AccessTokenBlacklist accessTokenBlacklist,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver
    ) {
        this.jwtProvider = jwtProvider;
        this.accessTokenBlacklist = accessTokenBlacklist;
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain
    ) throws ServletException, IOException {
        String token = extractAccessToken(request);
        if (token == null) {
            chain.doFilter(request, response);
            return;
        }
        try {
            JwtClaims claims = jwtProvider.parse(token);
            if (accessTokenBlacklist.isBlacklisted(claims.jti())) {
                throw new BusinessException(ErrorCode.AUTH_TOKEN_REVOKED);
            }
            Authentication auth = toAuthentication(claims);
            SecurityContextHolder.getContext().setAuthentication(auth);
            chain.doFilter(request, response);
        } catch (BusinessException e) {
            SecurityContextHolder.clearContext();
            resolver.resolveException(request, response, null, e);
        }
    }

    private String extractAccessToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (CookieUtil.ACCESS_TOKEN_COOKIE.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    private Authentication toAuthentication(JwtClaims claims) {
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + claims.role());
        return new UsernamePasswordAuthenticationToken(
                claims.userId(),   // principal = userId
                null,              // credentials
                List.of(authority)
        );
    }
}
