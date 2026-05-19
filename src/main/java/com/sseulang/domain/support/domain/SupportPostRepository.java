package com.sseulang.domain.support.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface SupportPostRepository {

    SupportPost save(SupportPost post);

    Optional<SupportPost> findById(Long id);

    void deleteById(Long id);

    
    Page<SupportPost> findVisible(SupportPostType type, InquiryCategory category, Pageable pageable);
}
