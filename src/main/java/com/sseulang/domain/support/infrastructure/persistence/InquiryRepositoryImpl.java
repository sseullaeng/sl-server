package com.sseulang.domain.support.infrastructure.persistence;

import com.sseulang.domain.support.domain.Inquiry;
import com.sseulang.domain.support.domain.InquiryRepository;
import com.sseulang.domain.support.domain.InquiryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class InquiryRepositoryImpl implements InquiryRepository {

    private final InquiryJpaRepository jpa;

    public InquiryRepositoryImpl(InquiryJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Inquiry save(Inquiry inquiry) {
        return jpa.save(inquiry);
    }

    @Override
    public Optional<Inquiry> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public void deleteById(Long id) {
        jpa.deleteById(id);
    }

    @Override
    public Page<Inquiry> findByUserId(Long userId, InquiryStatus status, Pageable pageable) {
        return jpa.findByUserId(userId, status, pageable);
    }

    @Override
    public Page<Inquiry> findAllForAdmin(InquiryStatus status, Pageable pageable) {
        return jpa.findAllForAdmin(status, pageable);
    }
}
