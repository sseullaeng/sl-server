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

/**
 * 토스 결제 webhook 의 body size 제한 필터 (follow-up #53).
 *
 * <p>{@code @RequestBody String} 으로 전체 payload 를 메모리에 적재하는 구조라
 * 외부에서 큰 body 를 던지면 OOM / GC 압박. nginx {@code client_max_body_size} 가 1차 방어,
 * 본 필터는 nginx 우회 / 직결 케이스 (개발/내부 호출) 를 위한 application-단 2차 방어.</p>
 *
 * <p>한도 초과 시 413 Payload Too Large 즉시 응답 — Spring DispatcherServlet 까지 가지 않음.</p>
 *
 * <p>매칭 path: {@code /api/v1/payments/webhook/**}. 다른 endpoint 에는 영향 X.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class WebhookBodySizeFilter extends OncePerRequestFilter {

    /** 토스 webhook payload 정상 크기는 ~3KB. 16KB 면 충분히 여유. */
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
        // Content-Length 미주입 (chunked 또는 누락) 도 거부 — webhook 은 Content-Length 명시 의무.
        if (contentLength < 0 || contentLength > MAX_BODY_BYTES) {
            writeRejection(response);
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * 공통 ApiResponse 형식과 일치하는 413 응답 + traceId 포함. ErrorCode 와 message 도 enum 에서.
     * GlobalExceptionHandler 를 거치지 않아 traceId 헤더는 TraceIdFilter 에서 이미 세팅됨 — MDC 에서 가져옴.
     */
    private static void writeRejection(HttpServletResponse response) throws IOException {
        ErrorCode code = ErrorCode.PAYLOAD_TOO_LARGE;
        response.setStatus(code.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);
        // JSON 수동 직렬화 — traceId/message 모두 안전 문자열만이라 escape 안전.
        String body = "{\"success\":false,\"error\":{"
                + "\"code\":\"" + code.name() + "\","
                + "\"message\":\"" + code.getDefaultMessage() + "\","
                + "\"traceId\":\"" + (traceId == null ? "" : traceId) + "\""
                + "}}";
        response.getWriter().write(body);
    }
}
