package com.sseulang.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인증 누락(401) 시 ApiResponse 형식 통일.
 * SecurityConfig 의 USER/ADMIN chain 에 등록 — Spring 기본 401 HTML 대신 표준 JSON 응답.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException
    ) throws IOException {
        ErrorCode code = ErrorCode.AUTH_TOKEN_MISSING;
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiResponse<Void> body = ApiResponse.fail(code.name(), code.getDefaultMessage(), MDC.get("traceId"));
        objectMapper.writeValue(response.getWriter(), body);
    }
}
