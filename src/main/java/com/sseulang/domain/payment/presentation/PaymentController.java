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
     * 토스 webhook — 토스 공식 spec (PAYMENT_STATUS_CHANGED) 처리.
     *
     * <p>Toss 발송 헤더:
     * <ul>
     *   <li>{@code tosspayments-webhook-transmission-id} — 멱등 키 (재전송 시 동일)</li>
     *   <li>{@code tosspayments-webhook-transmission-time} — 발송 시각 (감사용)</li>
     * </ul>
     *
     * <p>결제 이벤트에는 HMAC 시그니처가 없으므로 위변조 방지는 토스 lookup API 재조회로 처리
     * (게이트 1 round 1). 정상/멱등(중복 transmission-id) 시 200 → 재시도 중단. payload 형식 오류
     * 또는 transmission-id 누락 시 400 → 토스 재시도.</p>
     *
     * <p>SecurityConfig 의 CSRF / auth 면제 이미 적용 (Day 7).</p>
     */
    @PostMapping("/webhook/toss")
    public ApiResponse<Void> tossWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "tosspayments-webhook-transmission-id", required = false) String transmissionId
    ) {
        paymentService.handleWebhook(rawPayload, transmissionId);
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
