package com.sseulang.domain.payment.presentation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"success\":false,\"error\":{\"code\":\"PAYLOAD_TOO_LARGE\","
                  + "\"message\":\"webhook body size limit exceeded\"}}"
            );
            return;
        }
        chain.doFilter(request, response);
    }
}
