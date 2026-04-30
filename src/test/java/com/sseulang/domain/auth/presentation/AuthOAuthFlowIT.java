package com.sseulang.domain.auth.presentation;

import com.sseulang.domain.auth.application.OAuthLoginService;
import com.sseulang.domain.auth.application.RefreshTokenRotationService;
import com.sseulang.domain.auth.application.dto.TokenPair;
import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.global.common.TraceIdFilter;
import com.sseulang.global.exception.ErrorCode;
import com.sseulang.global.exception.GlobalExceptionHandler;
import com.sseulang.global.security.CookieUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/v1/auth/oauth2/* 의 통합 흐름 검증 — TraceIdFilter / X-Trace-Id 헤더 / 응답 본문
 * traceId 일관성. (Day 2 의 AuthSecurityFlowIT 가 SecurityConfig chain wiring 을, 본 IT 는
 * AuthController + filter + advice 의 응답 일관성을 닫는다.)
 */
class AuthOAuthFlowIT {

    private OAuthLoginService oauthLoginService;
    private RefreshTokenRotationService rotationService;
    private CookieUtil cookieUtil;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        oauthLoginService = mock(OAuthLoginService.class);
        rotationService = mock(RefreshTokenRotationService.class);
        cookieUtil = mock(CookieUtil.class);
        AuthController controller = new AuthController(
                rotationService, oauthLoginService,
                mock(com.sseulang.domain.auth.application.LocalAuthService.class),
                cookieUtil
        );

        mvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilter(new TraceIdFilter())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("oauth2/kakao 정상_200 + X-Trace-Id 헤더 + Set-Cookie 2 + ApiResponse")
    void oauth2_kakao_정상_traceId_헤더() throws Exception {
        when(oauthLoginService.login(eq(SocialProvider.KAKAO), eq("KAKAO_AT")))
                .thenReturn(new TokenPair("AT", "RT"));
        when(cookieUtil.accessTokenCookie("AT"))
                .thenReturn(ResponseCookie.from("at", "AT").path("/").httpOnly(true).build());
        when(cookieUtil.refreshTokenCookie("RT"))
                .thenReturn(ResponseCookie.from("rt", "RT").path("/api/v1/auth").httpOnly(true).build());

        MvcResult result = mvc.perform(post("/api/v1/auth/oauth2/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"KAKAO_AT\"}"))
                .andExpect(status().isOk())
                .andExpect(header().exists(TraceIdFilter.HEADER))
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).hasSize(2);
        assertThat(result.getResponse().getHeader(TraceIdFilter.HEADER)).isNotBlank();
    }

    @Test
    @DisplayName("oauth2/unknown 미지원 provider_401 + X-Trace-Id 헤더 ↔ 본문 error.traceId 일치")
    void oauth2_unknown_traceId_일관성() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/oauth2/twitter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"T\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(TraceIdFilter.HEADER))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.AUTH_OAUTH_FAILED.name()))
                .andExpect(jsonPath("$.error.traceId").exists())
                .andReturn();

        String headerTraceId = result.getResponse().getHeader(TraceIdFilter.HEADER);
        String body = result.getResponse().getContentAsString();

        assertThat(headerTraceId).isNotBlank();
        assertThat(body).contains("\"traceId\":\"" + headerTraceId + "\"");
    }

    @Test
    @DisplayName("oauth2 헤더의 X-Trace-Id 가 들어오면 그대로 사용 (relay)")
    void oauth2_traceId_relay() throws Exception {
        String incoming = "client-trace-12345";

        MvcResult result = mvc.perform(post("/api/v1/auth/oauth2/twitter")
                        .header(TraceIdFilter.HEADER, incoming)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"T\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(TraceIdFilter.HEADER, incoming))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"traceId\":\"" + incoming + "\"");
    }
}
