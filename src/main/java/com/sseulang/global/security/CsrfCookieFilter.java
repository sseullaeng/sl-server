package com.sseulang.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Spring Security 6 deferred CSRF token 동작 보정 — 매 응답에 XSRF-TOKEN 쿠키를 박아
 * SPA/curl 클라이언트가 다음 요청 헤더({@code X-XSRF-TOKEN})로 echo 가능.
 *
 * <p>Spring 6 부터 {@code CsrfToken} 은 lazy load 라 application code 가 명시적으로
 * {@code CsrfToken.getToken()} 호출하지 않으면 응답 쿠키에 박히지 않는다. 본 필터가
 * 모든 요청 종료 시점에 호출해 매 응답마다 새 토큰을 보장.</p>
 */
@Component
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrf != null) {
            csrf.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
