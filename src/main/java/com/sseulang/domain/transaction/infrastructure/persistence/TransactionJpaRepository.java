package com.sseulang.domain.transaction.infrastructure.persistence;

import com.sseulang.domain.transaction.domain.Transaction;
import com.sseulang.domain.transaction.domain.TransactionStatusCount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Spring Data JPA — {@link TransactionRepositoryImpl} 가 wrapping. 외부에서 직접 import 금지. */
interface TransactionJpaRepository extends JpaRepository<Transaction, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaction t WHERE t.id = :id")
    Optional<Transaction> findByIdForUpdate(@Param("id") Long id);

    /**
     * status 별 거래 건수 집계. 단일 쿼리 — N+1 없음. 결과는 status 가 한 번이라도 등장한 행만 옴
     * (0 건인 status 는 application 단에서 0 으로 채움). status 컬럼 인덱스 권장.
     */
    @Query("""
            SELECT new com.sseulang.domain.transaction.domain.TransactionStatusCount(t.status, COUNT(t))
              FROM Transaction t
             GROUP BY t.status
            """)
    List<TransactionStatusCount> countGroupByStatusJpql();
}
