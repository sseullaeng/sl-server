package com.sseulang.domain.payment.domain;

/**
 * 결제 PG 추상화. 도메인 layer 인터페이스 — 외부 SDK / HTTP 의존 X.
 * 구현은 {@code domain/payment/infrastructure/} (TossPaymentGateway). 향후 KakaoPay 등 추가 시 새 구현체.
 */
public interface PaymentGateway {

    /**
     * PG 의 confirm API 호출 — paymentKey 와 orderId 로 승인 처리.
     * <p>실패 시 {@link com.sseulang.global.exception.BusinessException} 또는
     * {@link com.sseulang.global.exception.ExternalApiException} throw.</p>
     */
    PaymentConfirmResult confirm(String paymentKey, String orderId, long amount);

    /**
     * paymentKey 로 PG 측 결제 상태 단건 조회. Codex 게이트 1 보강 — confirm 4xx ALREADY_PROCESSED
     * 응답 시 dangling 자동 복구 분기에서 사용. confirm 과 동일 형태의 결과 반환 (이미 승인된 결제만 호출).
     * <p>4xx (없는 paymentKey 등) → BusinessException, 5xx → ExternalApiException.</p>
     */
    PaymentConfirmResult lookup(String paymentKey);

    /**
     * orderId(merchantUid) 로 PG 측 결제 상태 조회. dangling 복구 스케줄러용 (follow-up #21):
     * confirm 도중 응답 유실로 paymentKey 를 못 받은 status=대기 Payment 를 orderId 만으로 토스 측 상태 동기화.
     * <p>4xx (없는 orderId 등) → BusinessException, 5xx → ExternalApiException.</p>
     */
    PaymentConfirmResult lookupByOrderId(String orderId);
}
