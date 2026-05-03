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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

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

    /**
     * 인증 없이 호출 가능한 auth endpoint 들. {@code /api/v1/auth/**} 통째로 permitAll 하지 않고
     * 개별 명시 — {@code /api/v1/auth/resend-verification} 같은 인증 필수 endpoint 가 실수로
     * permitAll 되는 회귀 차단 (게이트 1 round 2).
     */
    private static final String[] PUBLIC_AUTH_ENDPOINTS = {
            "/api/v1/auth/oauth2/**",
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
            "/api/v1/auth/admin/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/verify-email"
    };

    private static final String[] CSRF_IGNORED_ENDPOINTS = {
            // 위 PUBLIC_AUTH_ENDPOINTS 와 동일한 정책 — 익명 호출 endpoint 만 CSRF 면제.
            // resend-verification 은 인증 필수라 면제 X.
            "/api/v1/auth/oauth2/**",
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
            "/api/v1/auth/admin/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/verify-email",
            // WebSocket handshake 는 SockJS 폴백 path 까지 포함해 CSRF 면제 — STOMP CONNECT 단계의
            // 인증·인가는 ChannelInterceptor 가 별도 검증. native ws (follow-up #19) 도 동일.
            "/ws-stomp/**",
            "/ws-stomp-native/**",
            // 토스 webhook — 외부 PG 가 호출하는 콜백. 시그니처 검증은 webhook 핸들러 책임 (후속).
            "/api/v1/payments/webhook/**"
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

    /** 관리자 비밀번호 BCrypt 인코더. AdminLoginService 가 password 검증에 사용. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * REST CORS — {@code app.cors.allowed-origins} 화이트리스트. 와일드카드(*) X.
     * preflight(OPTIONS) 가 인증/CSRF 통과하지 못하던 회귀 차단 — Spring Security 의 cors() 가
     * 본 Bean 을 자동 사용해 OPTIONS 를 SecurityFilterChain 진입 전에 처리.
     *
     * <p>credentials=true (쿠키 동봉) 라서 Origin 정확 매칭 필수. WebSocket handshake 는 STOMP
     * endpoint 의 setAllowedOrigins 가 별도로 처리하므로 본 source 는 REST(/**) 에만.</p>
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Set-Cookie"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
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
                .securityMatcher("/api/v1/**", "/ws-stomp/**", "/ws-stomp-native/**")
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(eagerCsrfHandler())
                        .ignoringRequestMatchers(CSRF_IGNORED_ENDPOINTS))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_AUTH_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/items/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories/**").permitAll()
                        // 다른 사용자 공개 프로필 — ItemDetail 의 sellerId 등 비로그인 접근 허용.
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/*/profile").permitAll()
                        // 메인 화면 배너 / 공지 — 비로그인도 노출 (FRONTEND_INTEGRATION.md §10.8/10.9 정합).
                        .requestMatchers(HttpMethod.GET, "/api/v1/banners").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/notices/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/webhook/**").permitAll()
                        // WebSocket handshake 는 인증 없이 통과 — STOMP CONNECT 단계의 ChannelInterceptor 가
                        // Authorization 헤더 검증으로 인증 책임 (follow-up #19 native 토큰 인증).
                        .requestMatchers("/ws-stomp/**", "/ws-stomp-native/**").permitAll()
                        // ROLE_USER 강제 — ADMIN AT 가 user 영역(특히 출금/결제) 진입하지 못하도록 차단.
                        // adminId 와 userId 가 동일 숫자면 본인 검사도 통과해버리는 격리 누수 방지 (게이트 1).
                        .anyRequest().hasRole("USER"))
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
                // corsConfigurationSource Bean 자동 사용 — preflight(OPTIONS) 통과 + 쿠키 허용 헤더 박힘.
                .cors(c -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    }
}
