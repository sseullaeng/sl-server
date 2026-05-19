package com.sseulang.domain.payment.application;

import com.sseulang.domain.payment.domain.PaymentConfirmResult;
import com.sseulang.domain.payment.domain.PaymentGateway;
import com.sseulang.domain.payment.domain.PaymentMethod;

import java.time.LocalDateTime;

/**
 * 테스트용 PG fake — 토스 confirm/lookup 응답을 미리 설정. 실패 시나리오는 amountOverride /
 * confirmException / lookupAmountOverride 로.
 */
public class FakePaymentGateway implements PaymentGateway {

    public Long amountOverride;
    /** confirm 응답 orderId (PG echo 위변조 시뮬레이션). null 이면 호출 시 받은 orderId 그대로 echo. */
    public String confirmOrderIdOverride;
    public boolean failure;
    public RuntimeException failureException;
    /** confirm 호출 시 즉시 던질 예외 (BusinessException 등). null 이면 정상 흐름. */
    public RuntimeException confirmException;
    /** lookup 호출 시 즉시 던질 예외. null 이면 정상 흐름. */
    public RuntimeException lookupException;
    /** lookup 응답 amount (위변조 시뮬레이션). null 이면 가장 최근 confirm 의 amount 사용. */
    public Long lookupAmountOverride;
    /** lookup 응답 orderId (위변조 시뮬레이션). null 이면 가장 최근 confirm 의 orderId 사용. */
    public String lookupOrderIdOverride;

    public int confirmCalls;
    public int lookupCalls;
    public int lookupByOrderIdCalls;
    /** lookupByOrderId 응답 amount/orderId override (null 이면 호출 시 받은 orderId 그대로 echo, amount=lastConfirmAmount). */
    public Long lookupByOrderIdAmount;
    public RuntimeException lookupByOrderIdException;
    private long lastConfirmAmount;
    private String lastConfirmOrderId;

    @Override
    public PaymentConfirmResult confirm(String paymentKey, String orderId, long amount) {
        confirmCalls++;
        lastConfirmAmount = amount;
        lastConfirmOrderId = orderId;
        if (confirmException != null) {
            throw confirmException;
        }
        if (failure) {
            throw failureException != null ? failureException : new RuntimeException("test failure");
        }
        long resultAmount = amountOverride != null ? amountOverride : amount;
        String resultOrderId = confirmOrderIdOverride != null ? confirmOrderIdOverride : orderId;
        return new PaymentConfirmResult(
                paymentKey,
                resultOrderId,
                resultAmount,
                PaymentMethod.CARD,
                LocalDateTime.now(),
                "{\"raw\":\"fake\"}"
        );
    }

    @Override
    public PaymentConfirmResult lookup(String paymentKey) {
        lookupCalls++;
        if (lookupException != null) {
            throw lookupException;
        }
        long resultAmount = lookupAmountOverride != null ? lookupAmountOverride : lastConfirmAmount;
        String resultOrderId = lookupOrderIdOverride != null ? lookupOrderIdOverride : lastConfirmOrderId;
        return new PaymentConfirmResult(
                paymentKey,
                resultOrderId,
                resultAmount,
                PaymentMethod.CARD,
                LocalDateTime.now(),
                "{\"raw\":\"fake-lookup\"}"
        );
    }

    @Override
    public PaymentConfirmResult lookupByOrderId(String orderId) {
        lookupByOrderIdCalls++;
        if (lookupByOrderIdException != null) {
            throw lookupByOrderIdException;
        }
        long resultAmount = lookupByOrderIdAmount != null ? lookupByOrderIdAmount : lastConfirmAmount;
        return new PaymentConfirmResult(
                "fake-pk-" + orderId,
                orderId,
                resultAmount,
                PaymentMethod.CARD,
                LocalDateTime.now(),
                "{\"raw\":\"fake-lookup-by-orderId\"}"
        );
    }
}
