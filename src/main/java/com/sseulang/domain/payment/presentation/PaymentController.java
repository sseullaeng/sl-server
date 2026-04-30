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
     * 토스 webhook — 본 PR 에선 로깅만. 인증 X (외부 호출). 추후 시그니처 검증 + 멱등 처리는 후속 이슈.
     * SecurityConfig 의 CSRF / auth 면제 필요.
     */
    @PostMapping("/webhook/toss")
    public ApiResponse<Void> tossWebhook(@RequestBody String rawPayload) {
        paymentService.handleWebhook(rawPayload);
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
