package com.sseulang.global.config;

import com.sseulang.global.security.CsrfCookieFilter;
import com.sseulang.global.security.JwtAccessDeniedHandler;
import com.sseulang.global.security.JwtAuthenticationEntryPoint;
import com.sseulang.global.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * 3-chain 분리:
 *  - ADMIN ({@code /api/v1/admin/**}) : ROLE_ADMIN 강제 + JWT + CSRF
 *  - USER  ({@code /api/v1/**})       : 기본 authenticated, 일부 permitAll + JWT + CSRF
 *  - PUBLIC (그 외 정적·공개 자원)    : swagger/actuator 외 deny, CSRF 불필요
 *
 * CSRF 정책: 쿠키 인증이므로 {@code X-XSRF-TOKEN} 헤더로 검증 (CLAUDE.md §4 보안 룰).
 * SameSite=Strict 가 1차 방어, X-XSRF-TOKEN 이 2차. {@code /api/v1/auth/**} 는 면제.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/actuator/health",
            "/actuator/info"
    };

    private static final String[] AUTH_ENDPOINTS = {
            "/api/v1/auth/**"
    };

    private static final String[] CSRF_IGNORED_ENDPOINTS = {
            "/api/v1/auth/**",
            // WebSocket handshake 는 SockJS 폴백 path 까지 포함해 CSRF 면제 — STOMP CONNECT 단계의
            // 인증·인가는 ChannelInterceptor 가 별도 검증.
            "/ws-stomp/**"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;
    private final CsrfCookieFilter csrfCookieFilter;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            JwtAuthenticationEntryPoint authenticationEntryPoint,
            JwtAccessDeniedHandler accessDeniedHandler,
            CsrfCookieFilter csrfCookieFilter
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.csrfCookieFilter = csrfCookieFilter;
    }

    /**
     * CSRF token request handler — SPA / 쿠키 기반 클라이언트 호환 모드.
     * {@code setCsrfRequestAttributeName(null)} 로 deferred load 비활성 → 매 요청에 토큰 즉시 박힘
     * → GET 응답에서 토큰 invalidate 회귀 차단. (Spring Security 6.1+ 표준 SPA 패턴)
     */
    private static CsrfTokenRequestAttributeHandler eagerCsrfHandler() {
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName(null);
        return handler;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
        applyCommon(http)
                .securityMatcher("/api/v1/admin/**")
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(eagerCsrfHandler()))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("ADMIN"))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(csrfCookieFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain userFilterChain(HttpSecurity http) throws Exception {
        applyCommon(http)
                .securityMatcher("/api/v1/**", "/ws-stomp/**")
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(eagerCsrfHandler())
                        .ignoringRequestMatchers(CSRF_IGNORED_ENDPOINTS))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(AUTH_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/items/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(csrfCookieFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public SecurityFilterChain publicFilterChain(HttpSecurity http) throws Exception {
        applyCommon(http)
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }

    private HttpSecurity applyCommon(HttpSecurity http) throws Exception {
        return http
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    }
}
