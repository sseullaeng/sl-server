package com.sseulang.domain.support.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * Inquiry Aggregate Repository (도메인 인터페이스).
 *
 * <p>JPA 구현은 {@code infrastructure/persistence/}. 도메인 레이어는 Spring/JPA 의존 0.</p>
 */
public interface InquiryRepository {

    Inquiry save(Inquiry inquiry);

    Optional<Inquiry> findById(Long id);

    void deleteById(Long id);

    /** 본인 문의 목록. status 필터 nullable. 최신순. */
    Page<Inquiry> findByUserId(Long userId, InquiryStatus status, Pageable pageable);

    /** 관리자 전체 조회. status 필터 nullable. 최신순. */
    Page<Inquiry> findAllForAdmin(InquiryStatus status, Pageable pageable);
}
