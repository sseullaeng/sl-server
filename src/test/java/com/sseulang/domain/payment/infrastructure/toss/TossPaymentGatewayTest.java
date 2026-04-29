package com.sseulang.domain.payment.infrastructure.toss;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sseulang.domain.payment.domain.PaymentConfirmResult;
import com.sseulang.domain.payment.domain.PaymentMethod;
import com.sseulang.global.exception.ErrorCode;
import com.sseulang.global.exception.ExternalApiException;
import com.sseulang.global.infra.payment.TossProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Codex 게이트 1 보강 — 토스 응답 필드 검증 + 4xx body code 분기 단위 테스트.
 * RestClient end-to-end (4xx/5xx 실제 응답) 는 후속 IT 이슈에서 MockRestServiceServer 로 검증.
 */
class TossPaymentGatewayTest {

    private TossPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new TossPaymentGateway(
                RestClient.builder(),
                new ObjectMapper(),
                new TossProperties("ck", "sk", "https://api.tosspayments.com")
        );
    }

    // ───────── parse() — 정상 응답 ─────────

    @Test
    @DisplayName("parse 정상_모든 필드 매핑 + raw response 보존")
    void parse_정상() {
        String body = """
                {
                  "paymentKey": "tk-123",
                  "orderId": "charge-abc",
                  "totalAmount": 50000,
                  "status": "DONE",
                  "method": "카드",
                  "approvedAt": "2026-04-29T12:34:56+09:00"
                }
                """;

        PaymentConfirmResult r = gateway.parse(body);

        assertThat(r.paymentKey()).isEqualTo("tk-123");
        assertThat(r.orderId()).isEqualTo("charge-abc");
        assertThat(r.amount()).isEqualTo(50_000L);
        assertThat(r.method()).isEqualTo(PaymentMethod.CARD);
        assertThat(r.approvedAt()).isNotNull();
        assertThat(r.rawResponse()).isEqualTo(body);
    }

    @Test
    @DisplayName("parse method 영문_CARD/TRANSFER/VIRTUAL_ACCOUNT 매핑")
    void parse_method_영문() {
        assertThat(gateway.parse(jsonWithMethod("CARD")).method()).isEqualTo(PaymentMethod.CARD);
        assertThat(gateway.parse(jsonWithMethod("TRANSFER")).method()).isEqualTo(PaymentMethod.TRANSFER);
        assertThat(gateway.parse(jsonWithMethod("VIRTUAL_ACCOUNT")).method()).isEqualTo(PaymentMethod.VIRTUAL_ACCOUNT);
    }

    // ───────── parse() — 필수 필드 검증 ─────────

    @Test
    @DisplayName("parse paymentKey 누락_ExternalApiException")
    void parse_paymentKey_누락() {
        String body = """
                { "orderId": "o-1", "totalAmount": 1000, "method": "카드" }
                """;
        assertThatThrownBy(() -> gateway.parse(body))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("paymentKey");
    }

    @Test
    @DisplayName("parse orderId 빈문자열_ExternalApiException")
    void parse_orderId_blank() {
        String body = """
                { "paymentKey": "tk", "orderId": "", "totalAmount": 1000, "method": "카드" }
                """;
        assertThatThrownBy(() -> gateway.parse(body))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("orderId");
    }

    @Test
    @DisplayName("parse totalAmount 누락_ExternalApiException")
    void parse_totalAmount_누락() {
        String body = """
                { "paymentKey": "tk", "orderId": "o-1", "method": "카드" }
                """;
        assertThatThrownBy(() -> gateway.parse(body))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("totalAmount");
    }

    @Test
    @DisplayName("parse method 누락_ExternalApiException")
    void parse_method_누락() {
        String body = """
                { "paymentKey": "tk", "orderId": "o-1", "totalAmount": 1000, "status": "DONE" }
                """;
        assertThatThrownBy(() -> gateway.parse(body))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("method");
    }

    @Test
    @DisplayName("parse status 누락_ExternalApiException")
    void parse_status_누락() {
        String body = """
                { "paymentKey": "tk", "orderId": "o-1", "totalAmount": 1000, "method": "카드" }
                """;
        assertThatThrownBy(() -> gateway.parse(body))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("status");
    }

    @Test
    @DisplayName("parse status != DONE (READY/IN_PROGRESS/ABORTED)_BusinessException PAYMENT_VERIFY_FAILED")
    void parse_status_미승인() {
        for (String status : new String[]{"READY", "IN_PROGRESS", "ABORTED", "EXPIRED"}) {
            String body = """
                    {
                      "paymentKey": "tk", "orderId": "o-1", "totalAmount": 1000,
                      "status": "%s", "method": "카드",
                      "approvedAt": "2026-04-29T12:34:56+09:00"
                    }
                    """.formatted(status);
            assertThatThrownBy(() -> gateway.parse(body))
                    .as("status=%s 일 때 PAYMENT_VERIFY_FAILED", status)
                    .isInstanceOf(com.sseulang.global.exception.BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.PAYMENT_VERIFY_FAILED);
        }
    }

    @Test
    @DisplayName("parse approvedAt 누락_silent now() fallback 금지_ExternalApiException")
    void parse_approvedAt_누락() {
        String body = """
                { "paymentKey": "tk", "orderId": "o-1", "totalAmount": 1000,
                  "status": "DONE", "method": "카드" }
                """;
        assertThatThrownBy(() -> gateway.parse(body))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("approvedAt");
    }

    @Test
    @DisplayName("parse 미지원 method (간편결제 등)_silent null 금지_ExternalApiException")
    void parse_미지원_method() {
        String body = jsonWithMethod("간편결제");
        assertThatThrownBy(() -> gateway.parse(body))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("간편결제");
    }

    // ───────── extractErrorCode() — 4xx body 분기 ─────────

    @Test
    @DisplayName("extractErrorCode ALREADY_PROCESSED_PAYMENT_PAYMENT_ALREADY_PROCESSED")
    void extractErrorCode_alreadyProcessed() {
        String body = """
                { "code": "ALREADY_PROCESSED_PAYMENT", "message": "이미 처리된 결제" }
                """;
        assertThat(gateway.extractErrorCode(body)).isEqualTo(ErrorCode.PAYMENT_ALREADY_PROCESSED);
    }

    @Test
    @DisplayName("extractErrorCode ALREADY_COMPLETED_PAYMENT_PAYMENT_ALREADY_PROCESSED")
    void extractErrorCode_alreadyCompleted() {
        String body = """
                { "code": "ALREADY_COMPLETED_PAYMENT" }
                """;
        assertThat(gateway.extractErrorCode(body)).isEqualTo(ErrorCode.PAYMENT_ALREADY_PROCESSED);
    }

    @Test
    @DisplayName("extractErrorCode 그 외 code_PAYMENT_VERIFY_FAILED")
    void extractErrorCode_other() {
        String body = """
                { "code": "INVALID_CARD", "message": "카드 오류" }
                """;
        assertThat(gateway.extractErrorCode(body)).isEqualTo(ErrorCode.PAYMENT_VERIFY_FAILED);
    }

    @Test
    @DisplayName("extractErrorCode 빈 body_PAYMENT_VERIFY_FAILED (default)")
    void extractErrorCode_emptyBody() {
        assertThat(gateway.extractErrorCode("")).isEqualTo(ErrorCode.PAYMENT_VERIFY_FAILED);
        assertThat(gateway.extractErrorCode(null)).isEqualTo(ErrorCode.PAYMENT_VERIFY_FAILED);
    }

    @Test
    @DisplayName("extractErrorCode JSON 파싱 실패_PAYMENT_VERIFY_FAILED (fallback)")
    void extractErrorCode_invalidJson() {
        assertThat(gateway.extractErrorCode("not a json")).isEqualTo(ErrorCode.PAYMENT_VERIFY_FAILED);
    }

    private static String jsonWithMethod(String method) {
        return """
                {
                  "paymentKey": "tk",
                  "orderId": "o-1",
                  "totalAmount": 1000,
                  "status": "DONE",
                  "method": "%s",
                  "approvedAt": "2026-04-29T12:34:56+09:00"
                }
                """.formatted(method);
    }
}
