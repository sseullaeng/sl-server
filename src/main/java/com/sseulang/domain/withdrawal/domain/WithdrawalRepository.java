package com.sseulang.domain.withdrawal.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface WithdrawalRepository {

    Withdrawal save(Withdrawal withdrawal);

    Optional<Withdrawal> findById(Long id);

    

    Optional<Withdrawal> findByIdForUpdate(Long id);

    

    Optional<Withdrawal> findByIdAndUserIdForUpdate(Long id, Long userId);

    

    Optional<Withdrawal> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    Page<Withdrawal> findByUserId(Long userId, Pageable pageable);

    
    Page<Withdrawal> findByStatus(WithdrawalStatus status, Pageable pageable);

    

    List<WithdrawalStatusCount> countGroupByStatus();

    
    long sumCompletedAmount();
}
