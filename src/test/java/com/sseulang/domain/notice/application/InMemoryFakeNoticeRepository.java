package com.sseulang.domain.notice.application;

import com.sseulang.domain.notice.domain.Notice;
import com.sseulang.domain.notice.domain.NoticeRepository;
import com.sseulang.domain.notice.domain.NoticeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class InMemoryFakeNoticeRepository implements NoticeRepository {

    private final Map<Long, Notice> store = new HashMap<>();
    private long sequence = 0;

    @Override
    public Notice save(Notice notice) {
        if (notice.getId() == null) {
            ReflectionTestUtils.setField(notice, "id", ++sequence);
        }
        store.put(notice.getId(), notice);
        return notice;
    }

    @Override
    public Optional<Notice> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }

    @Override
    public Page<Notice> findVisible(LocalDateTime now, NoticeType type, Pageable pageable) {
        List<Notice> filtered = store.values().stream()
                .filter(n -> n.isVisibleAt(now))
                .filter(n -> type == null || n.getType() == type)
                .sorted(Comparator
                        .comparing(Notice::isPinned).reversed()
                        .thenComparing(Comparator.comparing(Notice::getId).reversed()))
                .toList();
        return new PageImpl<>(filtered, pageable, filtered.size());
    }

    @Override
    public Page<Notice> findAllForAdmin(NoticeType type, Pageable pageable) {
        List<Notice> filtered = store.values().stream()
                .filter(n -> type == null || n.getType() == type)
                .sorted(Comparator.comparing(Notice::getId).reversed())
                .toList();
        return new PageImpl<>(filtered, pageable, filtered.size());
    }

    int size() {
        return store.size();
    }
}
