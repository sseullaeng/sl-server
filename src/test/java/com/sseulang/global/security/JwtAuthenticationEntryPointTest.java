package com.sseulang.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationEntryPointTest {

    private final ObjectMapper om = new ObjectMapper();
    private final JwtAuthenticationEntryPoint entryPoint = new JwtAuthenticationEntryPoint(om);
    private final JwtAccessDeniedHandler deniedHandler = new JwtAccessDeniedHandler(om);

    @Test
    @DisplayName("EntryPoint commence_401 + AUTH_TOKEN_MISSING JSON 응답")
    void entryPoint_401_AUTH_TOKEN_MISSING() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        entryPoint.commence(req, res, new BadCredentialsException("anything"));

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentType()).startsWith("application/json");
        String body = res.getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("\"success\":false");
        assertThat(body).contains("\"code\":\"AUTH_TOKEN_MISSING\"");
    }

    @Test
    @DisplayName("AccessDeniedHandler handle_403 + FORBIDDEN JSON 응답")
    void accessDeniedHandler_403_FORBIDDEN() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        deniedHandler.handle(req, res, new AccessDeniedException("nope"));

        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentType()).startsWith("application/json");
        String body = res.getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("\"success\":false");
        assertThat(body).contains("\"code\":\"FORBIDDEN\"");
    }
}
