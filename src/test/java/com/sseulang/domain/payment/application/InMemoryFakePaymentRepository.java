package com.sseulang.domain.payment.application;

import com.sseulang.domain.payment.domain.Payment;
import com.sseulang.domain.payment.domain.PaymentRepository;
import com.sseulang.domain.payment.domain.PaymentStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryFakePaymentRepository implements PaymentRepository {

    private final Map<Long, Payment> store = new HashMap<>();
    private long sequence = 0;

    @Override
    public Optional<Payment> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Payment> findByMerchantUid(String merchantUid) {
        return store.values().stream()
                .filter(p -> p.getMerchantUid().equals(merchantUid))
                .findFirst();
    }

    @Override
    public Optional<Payment> findByMerchantUidForUpdate(String merchantUid) {
        // fake — 락 의미 없음. 동시성 IT 에서 prod 락 검증.
        return findByMerchantUid(merchantUid);
    }

    @Override
    public Payment save(Payment payment) {
        if (payment.getId() == null) {
            ReflectionTestUtils.setField(payment, "id", ++sequence);
        }
        store.put(payment.getId(), payment);
        return payment;
    }

    @Override
    public long sumPaidAmount() {
        return store.values().stream()
                .filter(p -> p.getStatus() == PaymentStatus.완료)
                .mapToLong(Payment::getAmount)
                .sum();
    }

    @Override
    public long countPaid() {
        return store.values().stream().filter(p -> p.getStatus() == PaymentStatus.완료).count();
    }
}
