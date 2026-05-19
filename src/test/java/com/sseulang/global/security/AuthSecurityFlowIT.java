package com.sseulang.global.security;

import com.sseulang.domain.auth.domain.AccessTokenBlacklist;
import com.sseulang.global.common.TraceIdFilter;
import com.sseulang.global.config.SecurityConfig;
import com.sseulang.global.exception.GlobalExceptionHandler;
import com.sseulang.global.exception.ErrorCode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security filter chain wiring + EntryPoint + traceId 일관성 통합 테스트.
 *
 * <p>실제 SecurityConfig 의 USER chain 이 동작하고, 인증 누락 시 EntryPoint 가 ApiResponse 응답을
 * 내려주며, X-Trace-Id 헤더와 본문 traceId 가 일치하는지 검증한다.</p>
 */
@WebMvcTest(controllers = ProtectedTestController.class)
@Import({
        SecurityConfig.class,
        com.sseulang.global.config.CorsProperties.class,
        com.sseulang.global.security.CsrfCookieFilter.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class,
        TraceIdFilter.class,
        GlobalExceptionHandler.class
})
@org.springframework.test.context.TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:3000"
})
class AuthSecurityFlowIT {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private JwtProvider jwtProvider;

    @MockBean
    private AccessTokenBlacklist accessTokenBlacklist;

    @Test
    @DisplayName("보호된 엔드포인트_토큰 없음_401 + AUTH_TOKEN_MISSING + X-Trace-Id 헤더와 본문 traceId 일치")
    @WithAnonymousUser
    void 보호된엔드포인트_토큰없음_401_traceId일관() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/test/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(TraceIdFilter.HEADER))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.AUTH_TOKEN_MISSING.name()))
                .andExpect(jsonPath("$.error.traceId").exists())
                .andReturn();

        String headerTraceId = result.getResponse().getHeader(TraceIdFilter.HEADER);
        String bodyJson = result.getResponse().getContentAsString();

        assertThat(headerTraceId)
                .as("X-Trace-Id 헤더와 응답 본문의 error.traceId 가 일치해야 한다")
                .isNotBlank();
        assertThat(bodyJson).contains("\"traceId\":\"" + headerTraceId + "\"");
    }

    @Test
    @DisplayName("유효한 AT 쿠키_보호된 엔드포인트 200")
    void 유효AT_보호된엔드포인트_200() throws Exception {
        JwtClaims claims = new JwtClaims(1L, "USER", "jti-1", null,
                Instant.parse("2026-04-28T03:00:00Z"),
                Instant.parse("2026-04-28T03:30:00Z"));
        when(jwtProvider.parse(any())).thenReturn(claims);
        when(accessTokenBlacklist.isBlacklisted("jti-1")).thenReturn(false);

        mvc.perform(get("/api/v1/test/protected")
                        .cookie(new Cookie(CookieUtil.ACCESS_TOKEN_COOKIE, "AT_VALUE"))
                        .with(req -> {
                            // CSRF 면제 — GET 요청은 어차피 CSRF 검증 안 함
                            return req;
                        }))
                .andExpect(status().isOk())
                .andExpect(header().exists(TraceIdFilter.HEADER));
    }

    @Test
    @DisplayName("ADMIN AT_user 영역 접근_403 (권한 격리: hasRole(\"USER\") 강제)")
    void adminAT_user엔드포인트_403() throws Exception {
        // 시나리오: adminId=1 인 ADMIN AT 가 /api/v1/test/protected (user chain) 접근 시도.
        // 이전 .authenticated() 정책에선 인증만 됐다고 통과 — 본인 검사가 숫자 id 충돌하면 사용자 자원 접근 가능.
        // 게이트 1 보강: hasRole("USER") 강제로 ADMIN AT 는 차단된다.
        JwtClaims adminClaims = new JwtClaims(1L, "ADMIN", "admin-jti", null,
                Instant.parse("2026-04-28T03:00:00Z"),
                Instant.parse("2026-04-28T03:30:00Z"));
        when(jwtProvider.parse(any())).thenReturn(adminClaims);
        when(accessTokenBlacklist.isBlacklisted("admin-jti")).thenReturn(false);

        mvc.perform(get("/api/v1/test/protected")
                        .cookie(new Cookie(CookieUtil.ACCESS_TOKEN_COOKIE, "ADMIN_AT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.FORBIDDEN.name()));
    }

    @Test
    @DisplayName("role 누락 AT_user 영역 접근_403 (변조 / 구버전 토큰 방어)")
    void roleMissingAT_user엔드포인트_403() throws Exception {
        // role=null 로 발급된 토큰은 어떤 역할도 받지 못해 ROLE_null 부여 → hasRole("USER") fail.
        // 명시적으로 권한 결과 검증해서 회귀 시 즉시 실패하도록.
        JwtClaims noRoleClaims = new JwtClaims(1L, null, "jti-x", null,
                Instant.parse("2026-04-28T03:00:00Z"),
                Instant.parse("2026-04-28T03:30:00Z"));
        when(jwtProvider.parse(any())).thenReturn(noRoleClaims);
        when(accessTokenBlacklist.isBlacklisted("jti-x")).thenReturn(false);

        mvc.perform(get("/api/v1/test/protected")
                        .cookie(new Cookie(CookieUtil.ACCESS_TOKEN_COOKIE, "NOROLE_AT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.FORBIDDEN.name()));
    }

    @Test
    @DisplayName("Public 엔드포인트(swagger)_인증 차단 안 됨")
    void public_swagger_인증차단X() throws Exception {
        // Springdoc 본체는 슬라이스에 없어 404 가 정상. PUBLIC chain 의 permitAll 매처가
        // 동작했는지(=401/403 이 아닌지)만 검증한다.
        int status = mvc.perform(get("/swagger-ui.html")).andReturn().getResponse().getStatus();
        assertThat(status).isNotIn(401, 403);
    }

    @Test
    @DisplayName("Public 엔드포인트(actuator/health)_인증 없이 통과")
    void public_actuator_통과() throws Exception {
        // health endpoint 가 미설치된 슬라이스 환경 — denyAll 이 아닌 permitAll 매처가 동작하는지만 검증.
        // 매처는 통과하지만 핸들러가 없어 404 가 나오는 게 정상. 401/403 이 아님을 확인.
        int status = mvc.perform(get("/actuator/health")).andReturn().getResponse().getStatus();
        assertThat(status)
                .as("PUBLIC chain 의 permitAll 매처에 잡혀야 — 401/403 이면 chain 분리 실패")
                .isNotIn(401, 403);
    }
}
