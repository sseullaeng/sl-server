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

@Component
public class TossPaymentGateway implements PaymentGateway {

    
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
                    
                    
                    .header("Idempotency-Key", paymentKey)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            
            
            
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

    
    PaymentConfirmResult parse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            
            
            String paymentKey = requireText(root, "paymentKey");
            String orderId = requireText(root, "orderId");
            long amount = requireLong(root, "totalAmount");
            String status = requireText(root, "status");
            
            
            if (!"DONE".equals(status)) {
                throw new BusinessException(ErrorCode.PAYMENT_VERIFY_FAILED);
            }
            String methodRaw = root.path("method").asText("");
            PaymentMethod method = mapMethod(methodRaw);
            
            
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
