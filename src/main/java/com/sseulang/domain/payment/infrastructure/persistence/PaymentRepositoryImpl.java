package com.sseulang.domain.payment.infrastructure.persistence;

import com.sseulang.domain.payment.domain.Payment;
import com.sseulang.domain.payment.domain.PaymentRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository jpa;

    public PaymentRepositoryImpl(PaymentJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Payment> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<Payment> findByMerchantUid(String merchantUid) {
        return jpa.findByMerchantUid(merchantUid);
    }

    @Override
    public Optional<Payment> findByMerchantUidForUpdate(String merchantUid) {
        return jpa.findByMerchantUidForUpdate(merchantUid);
    }

    @Override
    public Payment save(Payment payment) {
        return jpa.save(payment);
    }

    @Override
    public long sumPaidAmount() {
        return jpa.sumPaidAmount();
    }

    @Override
    public long countPaid() {
        return jpa.countPaid();
    }
}
