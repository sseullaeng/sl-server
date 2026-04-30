package com.sseulang.domain.notice.infrastructure.persistence;

import com.sseulang.domain.notice.domain.Notice;
import com.sseulang.domain.notice.domain.NoticeRepository;
import com.sseulang.domain.notice.domain.NoticeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class NoticeRepositoryImpl implements NoticeRepository {

    private final NoticeJpaRepository jpa;

    public NoticeRepositoryImpl(NoticeJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Notice save(Notice notice) {
        return jpa.save(notice);
    }

    @Override
    public Optional<Notice> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public void deleteById(Long id) {
        jpa.deleteById(id);
    }

    @Override
    public Page<Notice> findVisible(LocalDateTime now, NoticeType type, Pageable pageable) {
        return jpa.findVisible(now, type, pageable);
    }

    @Override
    public Page<Notice> findAllForAdmin(NoticeType type, Pageable pageable) {
        return jpa.findAllForAdmin(type, pageable);
    }
}
