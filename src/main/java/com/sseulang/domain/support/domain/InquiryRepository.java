package com.sseulang.domain.support.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface InquiryRepository {

    Inquiry save(Inquiry inquiry);

    Optional<Inquiry> findById(Long id);

    void deleteById(Long id);

    
    Page<Inquiry> findByUserId(Long userId, InquiryStatus status, Pageable pageable);

    
    Page<Inquiry> findAllForAdmin(InquiryStatus status, Pageable pageable);
}
