package com.sseulang.domain.payment.presentation;

import com.sseulang.domain.payment.application.PaymentApplicationService;
import com.sseulang.domain.payment.presentation.dto.ChargeConfirmRequest;
import com.sseulang.domain.payment.presentation.dto.ChargeStartRequest;
import com.sseulang.domain.payment.presentation.dto.ChargeStartResponse;
import com.sseulang.domain.payment.presentation.dto.PaymentResponse;
import com.sseulang.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
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

    @Operation(summary = "충전 시작",
            description = "백엔드가 merchantUid 발급 + tossClientKey 반환. 응답 받은 즉시 토스 SDK 결제창 호출. 이메일 인증 필수.")
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

    @Operation(summary = "충전 확정",
            description = "토스 SDK 결제 성공 콜백 후 호출. 백엔드가 토스 confirm API 로 amount 재검증 (위변조 차단) → 잔액 충전. "
                    + "amount mismatch 400 PAYMENT_AMOUNT_MISMATCH, 같은 merchantUid 재호출 409 PAYMENT_DUPLICATED.")
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
    @Operation(summary = "토스 결제 webhook (외부)",
            description = "토스에서 호출. 인증/CSRF 면제. transmission-id 로 멱등성 보장. "
                    + "위변조 방지는 토스 lookup API 재조회로 검증. 프론트는 호출하지 않음.")
    @PostMapping("/webhook/toss")
    public ApiResponse<Void> tossWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "tosspayments-webhook-transmission-id", required = false) String transmissionId
    ) {
        paymentService.handleWebhook(rawPayload, transmissionId);
        return ApiResponse.ok();
    }

    @Operation(summary = "결제 단건 조회",
            description = "본인 결제만. status 가 PENDING 이면 webhook 또는 5분 reconciliation scheduler 대기 중.")
    @GetMapping("/{id}")
    public ApiResponse<PaymentResponse> getOne(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") Long id
    ) {
        return ApiResponse.ok(PaymentResponse.from(paymentService.getById(id, userId)));
    }
}
