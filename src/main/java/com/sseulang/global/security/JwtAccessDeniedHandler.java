package com.sseulang.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 권한 부족(403) 시 ApiResponse 형식 통일.
 * 인증은 됐지만 ROLE_ADMIN 등 권한이 부족할 때.
 */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JwtAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException
    ) throws IOException {
        ErrorCode code = ErrorCode.FORBIDDEN;
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiResponse<Void> body = ApiResponse.fail(code.name(), code.getDefaultMessage(), MDC.get("traceId"));
        objectMapper.writeValue(response.getWriter(), body);
    }
}
