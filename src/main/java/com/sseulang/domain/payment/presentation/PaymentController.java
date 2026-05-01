package com.sseulang.domain.payment.presentation;

import com.sseulang.domain.payment.application.PaymentApplicationService;
import com.sseulang.domain.payment.presentation.dto.ChargeConfirmRequest;
import com.sseulang.domain.payment.presentation.dto.ChargeStartRequest;
import com.sseulang.domain.payment.presentation.dto.ChargeStartResponse;
import com.sseulang.domain.payment.presentation.dto.PaymentResponse;
import com.sseulang.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payment", description = "토스 페이먼츠 충전 — startCharge → 토스 SDK 결제 → confirmCharge.")
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentApplicationService paymentService;

    public PaymentController(PaymentApplicationService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/charge")
    public ResponseEntity<ApiResponse<ChargeStartResponse>> startCharge(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ChargeStartRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(ChargeStartResponse.from(
                        paymentService.startCharge(request.toCommand(userId))
                )));
    }

    @PostMapping("/charge/confirm")
    public ApiResponse<PaymentResponse> confirmCharge(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ChargeConfirmRequest request
    ) {
        return ApiResponse.ok(PaymentResponse.from(
                paymentService.confirmCharge(request.toCommand(userId))
        ));
    }

    /**
     * 토스 webhook — 시그니처 + replay window + eventId 멱등 처리.
     *
     * <p>Toss 콘솔 발송 헤더 (이름은 Toss 가이드 변경 시 application.yml 의 별칭 도입 후 추상화 가능):
     * <ul>
     *   <li>{@code Tosspayments-Webhook-Signature} — HMAC-SHA256 (timestamp + body, webhookSecret)</li>
     *   <li>{@code Tosspayments-Webhook-Timestamp} — epoch seconds (replay window 검증용)</li>
     * </ul>
     * 검증 실패 시 401/400 → 토스가 재시도. 정상/멱등(중복 eventId) 시 200 → 재시도 중단.
     * SecurityConfig 의 CSRF / auth 면제 이미 적용 (Day 7).
     */
    @PostMapping("/webhook/toss")
    public ApiResponse<Void> tossWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "Tosspayments-Webhook-Signature", required = false) String signature,
            @RequestHeader(value = "Tosspayments-Webhook-Timestamp", required = false) String timestamp
    ) {
        paymentService.handleWebhook(rawPayload, signature, timestamp);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}")
    public ApiResponse<PaymentResponse> getOne(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") Long id
    ) {
        return ApiResponse.ok(PaymentResponse.from(paymentService.getById(id, userId)));
    }
}
