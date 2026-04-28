package com.sseulang.domain.auth.presentation;

import com.sseulang.domain.auth.application.RefreshTokenRotationService;
import com.sseulang.domain.auth.application.dto.TokenPair;
import com.sseulang.global.exception.GlobalExceptionHandler;
import com.sseulang.global.security.CookieUtil;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private RefreshTokenRotationService rotationService;
    private CookieUtil cookieUtil;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        rotationService = mock(RefreshTokenRotationService.class);
        cookieUtil = mock(CookieUtil.class);
        AuthController controller = new AuthController(rotationService, cookieUtil);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /auth/refresh_RT 쿠키 없음_401 AUTH_TOKEN_MISSING")
    void refresh_쿠키없음_401() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_MISSING"));

        verify(rotationService, never()).rotate(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("POST /auth/refresh_RT 쿠키 정상_새 AT/RT Set-Cookie 응답")
    void refresh_정상_쿠키set() throws Exception {
        when(rotationService.rotate("OLD_RT")).thenReturn(new TokenPair("NEW_AT", "NEW_RT"));
        when(cookieUtil.accessTokenCookie("NEW_AT"))
                .thenReturn(ResponseCookie.from("at", "NEW_AT").path("/").httpOnly(true).maxAge(1800).build());
        when(cookieUtil.refreshTokenCookie("NEW_RT"))
                .thenReturn(ResponseCookie.from("rt", "NEW_RT").path("/api/v1/auth").httpOnly(true).maxAge(604800).build());

        MvcResult result = mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(CookieUtil.REFRESH_TOKEN_COOKIE, "OLD_RT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).hasSize(2);
        assertThat(setCookies).anyMatch(s -> s.startsWith("at=NEW_AT"));
        assertThat(setCookies).anyMatch(s -> s.startsWith("rt=NEW_RT"));

        verify(rotationService, times(1)).rotate("OLD_RT");
    }

    @Test
    @DisplayName("POST /auth/logout_AT/RT 쿠키 모두 있음_logout(at, rt) 호출 + 삭제 쿠키 응답")
    void logout_AT_RT_둘다_있음() throws Exception {
        when(cookieUtil.deleteAccessTokenCookie())
                .thenReturn(ResponseCookie.from("at", "").path("/").httpOnly(true).maxAge(0).build());
        when(cookieUtil.deleteRefreshTokenCookie())
                .thenReturn(ResponseCookie.from("rt", "").path("/api/v1/auth").httpOnly(true).maxAge(0).build());

        MvcResult result = mvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie(CookieUtil.ACCESS_TOKEN_COOKIE, "AT_VALUE"))
                        .cookie(new Cookie(CookieUtil.REFRESH_TOKEN_COOKIE, "RT_VALUE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).hasSize(2);
        assertThat(setCookies).anyMatch(s -> s.contains("Max-Age=0"));

        verify(rotationService, times(1)).logout(eq("AT_VALUE"), eq("RT_VALUE"));
    }

    @Test
    @DisplayName("POST /auth/logout_쿠키 모두 없음_service.logout(null, null) 호출 + 삭제 쿠키 응답")
    void logout_쿠키없음() throws Exception {
        when(cookieUtil.deleteAccessTokenCookie())
                .thenReturn(ResponseCookie.from("at", "").path("/").httpOnly(true).maxAge(0).build());
        when(cookieUtil.deleteRefreshTokenCookie())
                .thenReturn(ResponseCookie.from("rt", "").path("/api/v1/auth").httpOnly(true).maxAge(0).build());

        mvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 쿠키 없으면 둘 다 null — service 가 noop 처리
        verify(rotationService, times(1)).logout(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull());
    }
}
