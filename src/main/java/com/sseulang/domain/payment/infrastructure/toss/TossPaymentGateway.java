package com.sseulang.domain.payment.infrastructure.toss;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sseulang.domain.payment.domain.PaymentConfirmResult;
import com.sseulang.domain.payment.domain.PaymentGateway;
import com.sseulang.domain.payment.domain.PaymentMethod;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import com.sseulang.global.exception.ExternalApiException;
import com.sseulang.global.infra.payment.TossProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;

/**
 * 토스페이먼츠 confirm API 어댑터. 가이드 §4.9 — 결제 완료 후 백엔드가 토스 API 로 금액 재검증.
 *
 * <ul>
 *   <li>POST {@code /v1/payments/confirm} — Basic auth (secret_key:)</li>
 *   <li>Body: {paymentKey, orderId, amount}</li>
 *   <li>4xx body.code 가 ALREADY_PROCESSED_PAYMENT 계열 → {@link ErrorCode#PAYMENT_ALREADY_PROCESSED}
 *       (Codex 게이트 1 보강 — dangling 복구 후속 잡이 분기 처리 가능하도록 분리)</li>
 *   <li>그 외 4xx → {@link ErrorCode#PAYMENT_VERIFY_FAILED} (위변조 / 잘못된 결제)</li>
 *   <li>5xx / 네트워크 / 응답 파싱 실패 → {@link ExternalApiException} (재시도 영역)</li>
 * </ul>
 */
@Component
public class TossPaymentGateway implements PaymentGateway {

    /** 토스 4xx body.code — 같은 paymentKey/orderId 가 이미 승인된 상태. */
    private static final java.util.Set<String> ALREADY_PROCESSED_CODES = java.util.Set.of(
            "ALREADY_PROCESSED_PAYMENT",
            "ALREADY_COMPLETED_PAYMENT"
    );

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String authorizationHeader;

    public TossPaymentGateway(RestClient.Builder builder, ObjectMapper objectMapper, TossProperties props) {
        this.restClient = builder
                .baseUrl(props.baseUrl())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = objectMapper;
        // Basic auth — username=secret_key, password=빈문자열. 토스 가이드 패턴.
        String token = Base64.getEncoder().encodeToString(
                (props.secretKey() + ":").getBytes(StandardCharsets.UTF_8)
        );
        this.authorizationHeader = "Basic " + token;
    }

    @Override
    public PaymentConfirmResult confirm(String paymentKey, String orderId, long amount) {
        Map<String, Object> body = Map.of(
                "paymentKey", paymentKey,
                "orderId", orderId,
                "amount", amount
        );
        String response;
        try {
            response = restClient.post()
                    .uri("/v1/payments/confirm")
                    .header("Authorization", authorizationHeader)
                    // Idempotency-Key: 같은 paymentKey 로 confirm 재시도 시 토스가 동일 응답 반환 (follow-up #21).
                    // 응답 유실/네트워크 타임아웃 후 재시도 안전 — paymentKey 자체가 결제 단위 멱등 키.
                    .header("Idempotency-Key", paymentKey)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            // 4xx — 토스가 명시 거부. body.code 별로 분기:
            //   - ALREADY_PROCESSED_PAYMENT 계열 → PAYMENT_ALREADY_PROCESSED (dangling 복구 후속 잡 분기 가능)
            //   - 그 외 → PAYMENT_VERIFY_FAILED (위변조/잘못된 결제)
            if (e.getStatusCode().is4xxClientError()) {
                throw new BusinessException(extractErrorCode(e.getResponseBodyAsString()));
            }
            throw new ExternalApiException("토스 결제 confirm 실패", e);
        } catch (Exception e) {
            throw new ExternalApiException("토스 결제 confirm 통신 실패", e);
        }
        return parse(response);
    }

    @Override
    public PaymentConfirmResult lookup(String paymentKey) {
        String response;
        try {
            response = restClient.get()
                    .uri("/v1/payments/{paymentKey}", paymentKey)
                    .header("Authorization", authorizationHeader)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                // 없는 paymentKey 등 — 위변조/잘못된 요청 취급.
                throw new BusinessException(ErrorCode.PAYMENT_VERIFY_FAILED);
            }
            throw new ExternalApiException("토스 결제 조회 실패", e);
        } catch (Exception e) {
            throw new ExternalApiException("토스 결제 조회 통신 실패", e);
        }
        return parse(response);
    }

    @Override
    public PaymentConfirmResult lookupByOrderId(String orderId) {
        String response;
        try {
            response = restClient.get()
                    .uri("/v1/payments/orders/{orderId}", orderId)
                    .header("Authorization", authorizationHeader)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                throw new BusinessException(ErrorCode.PAYMENT_VERIFY_FAILED);
            }
            throw new ExternalApiException("토스 결제 조회(orderId) 실패", e);
        } catch (Exception e) {
            throw new ExternalApiException("토스 결제 조회(orderId) 통신 실패", e);
        }
        return parse(response);
    }

    /** package-private — 단위 테스트에서 4xx body 분기 직접 검증. */
    ErrorCode extractErrorCode(String body) {
        if (body == null || body.isBlank()) {
            return ErrorCode.PAYMENT_VERIFY_FAILED;
        }
        try {
            String code = objectMapper.readTree(body).path("code").asText("");
            return ALREADY_PROCESSED_CODES.contains(code)
                    ? ErrorCode.PAYMENT_ALREADY_PROCESSED
                    : ErrorCode.PAYMENT_VERIFY_FAILED;
        } catch (Exception ignore) {
            return ErrorCode.PAYMENT_VERIFY_FAILED;
        }
    }

    /** package-private — 단위 테스트에서 응답 파싱/필드 검증 직접 호출. */
    PaymentConfirmResult parse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            // Codex 게이트 1 보강 — 필수 필드 누락 시 silent fallback 금지.
            // 빈 paymentKey / orderId 또는 0 amount 가 그대로 저장되면 후속 정합성 검증/조회가 깨진다.
            String paymentKey = requireText(root, "paymentKey");
            String orderId = requireText(root, "orderId");
            long amount = requireLong(root, "totalAmount");
            String status = requireText(root, "status");
            // 2차 재리뷰 보강 — confirm/lookup 모두 status=DONE 만 정상 승인. READY/IN_PROGRESS/ABORTED/EXPIRED
            // 등이 도달하면 잘못된 markAsPaid 를 막기 위해 차단 (BusinessException 으로 트랜잭션 롤백).
            if (!"DONE".equals(status)) {
                throw new BusinessException(ErrorCode.PAYMENT_VERIFY_FAILED);
            }
            String methodRaw = root.path("method").asText("");
            PaymentMethod method = mapMethod(methodRaw);
            // 2차 재리뷰 보강 — approvedAt 누락 시 LocalDateTime.now() silent fallback 제거.
            // 승인 시각 없는 응답은 토스 측 비정상 — 명시 실패.
            String approvedAt = root.path("approvedAt").asText(null);
            if (approvedAt == null || approvedAt.isBlank()) {
                throw new ExternalApiException("toss", "응답 approvedAt 누락");
            }
            LocalDateTime approved = OffsetDateTime.parse(approvedAt).toLocalDateTime();
            return new PaymentConfirmResult(paymentKey, orderId, amount, method, approved, response);
        } catch (BusinessException | ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("toss", "응답 파싱 실패: " + e.getMessage());
        }
    }

    private static String requireText(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (node.isMissingNode() || node.isNull()) {
            throw new ExternalApiException("toss", "응답 필수 필드 누락: " + field);
        }
        String value = node.asText("");
        if (value.isBlank()) {
            throw new ExternalApiException("toss", "응답 필수 필드 비어있음: " + field);
        }
        return value;
    }

    private static long requireLong(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (node.isMissingNode() || node.isNull() || !node.canConvertToLong()) {
            throw new ExternalApiException("toss", "응답 필수 필드 누락/형식오류: " + field);
        }
        return node.asLong();
    }

    /**
     * 토스 method → 도메인 enum 매핑. 미지원 method 는 silent null 금지 — 명시 실패로 운영 알림.
     * 신규 결제수단 (간편결제, 휴대폰 등) 출시 시 본 매핑을 먼저 확장해야 안전하게 활성화된다.
     */
    private static PaymentMethod mapMethod(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ExternalApiException("toss", "응답 method 누락");
        }
        return switch (raw.trim()) {
            case "카드", "CARD" -> PaymentMethod.CARD;
            case "계좌이체", "TRANSFER" -> PaymentMethod.TRANSFER;
            case "가상계좌", "VIRTUAL_ACCOUNT" -> PaymentMethod.VIRTUAL_ACCOUNT;
            default -> throw new ExternalApiException("toss", "지원하지 않는 결제수단: " + raw);
        };
    }
}
