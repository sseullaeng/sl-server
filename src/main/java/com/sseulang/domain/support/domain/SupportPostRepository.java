package com.sseulang.domain.support.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * SupportPost Aggregate Repository (도메인 인터페이스).
 *
 * <p>JPA 구현은 {@code infrastructure/persistence/}.</p>
 */
public interface SupportPostRepository {

    SupportPost save(SupportPost post);

    Optional<SupportPost> findById(Long id);

    void deleteById(Long id);

    /** 공개 목록 — type 필수, category nullable. 최신순. */
    Page<SupportPost> findVisible(SupportPostType type, InquiryCategory category, Pageable pageable);
}
