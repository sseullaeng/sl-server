package com.sseulang.domain.payment.presentation;

import com.sseulang.global.common.TraceIdFilter;
import com.sseulang.global.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class WebhookBodySizeFilter extends OncePerRequestFilter {

    
    static final long MAX_BODY_BYTES = 16L * 1024;

    private static final String WEBHOOK_PATH_PREFIX = "/api/v1/payments/webhook/";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(WEBHOOK_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        
        if (contentLength < 0 || contentLength > MAX_BODY_BYTES) {
            writeRejection(response);
            return;
        }
        chain.doFilter(request, response);
    }

    

    private static void writeRejection(HttpServletResponse response) throws IOException {
        ErrorCode code = ErrorCode.PAYLOAD_TOO_LARGE;
        response.setStatus(code.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);
        
        String body = "{\"success\":false,\"error\":{"
                + "\"code\":\"" + code.name() + "\","
                + "\"message\":\"" + code.getDefaultMessage() + "\","
                + "\"traceId\":\"" + (traceId == null ? "" : traceId) + "\""
                + "}}";
        response.getWriter().write(body);
    }
}
