package com.sseulang.domain.payment.domain;

public interface PaymentGateway {

    

    PaymentConfirmResult confirm(String paymentKey, String orderId, long amount);

    

    PaymentConfirmResult lookup(String paymentKey);

    

    PaymentConfirmResult lookupByOrderId(String orderId);
}
