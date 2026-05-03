package com.sseulang.domain.support.infrastructure.persistence;

import com.sseulang.domain.support.domain.InquiryCategory;
import com.sseulang.domain.support.domain.SupportPost;
import com.sseulang.domain.support.domain.SupportPostRepository;
import com.sseulang.domain.support.domain.SupportPostType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class SupportPostRepositoryImpl implements SupportPostRepository {

    private final SupportPostJpaRepository jpa;

    public SupportPostRepositoryImpl(SupportPostJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public SupportPost save(SupportPost post) {
        return jpa.save(post);
    }

    @Override
    public Optional<SupportPost> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public void deleteById(Long id) {
        jpa.deleteById(id);
    }

    @Override
    public Page<SupportPost> findVisible(SupportPostType type, InquiryCategory category, Pageable pageable) {
        return jpa.findVisible(type, category, pageable);
    }
}
