package com.sseulang.domain.support.application;

import com.sseulang.domain.support.domain.Inquiry;
import com.sseulang.domain.support.domain.InquiryRepository;
import com.sseulang.domain.support.domain.InquiryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class InMemoryFakeInquiryRepository implements InquiryRepository {

    private final Map<Long, Inquiry> store = new HashMap<>();
    private long sequence = 0;

    @Override
    public Inquiry save(Inquiry inquiry) {
        if (inquiry.getId() == null) {
            ReflectionTestUtils.setField(inquiry, "id", ++sequence);
        }
        store.put(inquiry.getId(), inquiry);
        return inquiry;
    }

    @Override
    public Optional<Inquiry> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }

    @Override
    public Page<Inquiry> findByUserId(Long userId, InquiryStatus status, Pageable pageable) {
        List<Inquiry> filtered = store.values().stream()
                .filter(i -> i.getUserId().equals(userId))
                .filter(i -> status == null || i.getStatus() == status)
                .sorted(Comparator.comparing(Inquiry::getId).reversed())
                .toList();
        return new PageImpl<>(filtered, pageable, filtered.size());
    }

    @Override
    public Page<Inquiry> findAllForAdmin(InquiryStatus status, Pageable pageable) {
        List<Inquiry> filtered = store.values().stream()
                .filter(i -> status == null || i.getStatus() == status)
                .sorted(Comparator.comparing(Inquiry::getId).reversed())
                .toList();
        return new PageImpl<>(filtered, pageable, filtered.size());
    }

    int size() {
        return store.size();
    }
}
