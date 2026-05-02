package com.sseulang.domain.payment.presentation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class WebhookBodySizeFilterTest {

    private WebhookBodySizeFilter filter;
    private FilterChain chain;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new WebhookBodySizeFilter();
        chain = mock(FilterChain.class);
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("webhook path + body 16KB 초과_413 + chain 미호출")
    void webhook_초과_413() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/payments/webhook/toss");
        req.setContentType("application/json");
        req.addHeader("Content-Length", String.valueOf(WebhookBodySizeFilter.MAX_BODY_BYTES + 1));
        req.setContent(new byte[(int) WebhookBodySizeFilter.MAX_BODY_BYTES + 1]);

        filter.doFilter(req, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("PAYLOAD_TOO_LARGE");
        verify(chain, never()).doFilter(req, response);
    }

    @Test
    @DisplayName("webhook path + body 정확히 16KB_통과")
    void webhook_정확_16KB_통과() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/payments/webhook/toss");
        req.setContent(new byte[(int) WebhookBodySizeFilter.MAX_BODY_BYTES]);

        filter.doFilter(req, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain, times(1)).doFilter(req, response);
    }

    @Test
    @DisplayName("webhook 외 path_size 무관 통과 (필터 미적용)")
    void 비webhook_path_미적용() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/items");
        req.setContent(new byte[1_000_000]);  // 1MB

        filter.doFilter(req, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain, times(1)).doFilter(req, response);
    }

    @Test
    @DisplayName("webhook path + Content-Length 미주입_413 거부")
    void webhook_Content_Length_미주입_거부() throws ServletException, IOException {
        // chunked 또는 누락 — getContentLengthLong() == -1
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/payments/webhook/toss");
        // setContent 호출 안 함 + Content-Length 헤더도 없음

        filter.doFilter(req, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        verify(chain, never()).doFilter(req, response);
    }
}
