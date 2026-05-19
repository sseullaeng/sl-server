package com.sseulang.domain.payment.domain;

import java.util.Optional;

public interface PaymentRepository {

    Optional<Payment> findById(Long id);

    Optional<Payment> findByMerchantUid(String merchantUid);

    
    Optional<Payment> findByMerchantUidForUpdate(String merchantUid);

    Payment save(Payment payment);

    

    java.util.List<Payment> findStalePending(java.time.LocalDateTime cutoff, int limit);

    

    
    long sumPaidAmount();

    
    long countPaid();
}
