package com.sseulang.domain.support.infrastructure.persistence;

import com.sseulang.domain.support.domain.Inquiry;
import com.sseulang.domain.support.domain.InquiryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA — {@link InquiryRepositoryImpl} 가 wrapping. */
interface InquiryJpaRepository extends JpaRepository<Inquiry, Long> {

    @Query("""
            SELECT i FROM Inquiry i
             WHERE i.userId = :userId
               AND (:status IS NULL OR i.status = :status)
             ORDER BY i.id DESC
            """)
    Page<Inquiry> findByUserId(
            @Param("userId") Long userId,
            @Param("status") InquiryStatus status,
            Pageable pageable
    );

    @Query("""
            SELECT i FROM Inquiry i
             WHERE (:status IS NULL OR i.status = :status)
             ORDER BY i.id DESC
            """)
    Page<Inquiry> findAllForAdmin(@Param("status") InquiryStatus status, Pageable pageable);
}
