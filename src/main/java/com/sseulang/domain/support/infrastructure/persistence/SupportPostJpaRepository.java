package com.sseulang.domain.support.infrastructure.persistence;

import com.sseulang.domain.support.domain.InquiryCategory;
import com.sseulang.domain.support.domain.SupportPost;
import com.sseulang.domain.support.domain.SupportPostType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SupportPostJpaRepository extends JpaRepository<SupportPost, Long> {

    @Query("""
            SELECT p FROM SupportPost p
             WHERE p.postType = :type
               AND (:category IS NULL OR p.category = :category)
             ORDER BY p.id DESC
            """)
    Page<SupportPost> findVisible(
            @Param("type") SupportPostType type,
            @Param("category") InquiryCategory category,
            Pageable pageable
    );
}
