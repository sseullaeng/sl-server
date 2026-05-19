package com.sseulang.domain.notice.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Optional;

public interface NoticeRepository {

    Notice save(Notice notice);

    Optional<Notice> findById(Long id);

    void deleteById(Long id);

    

    Page<Notice> findVisible(LocalDateTime now, NoticeType type, Pageable pageable);

    
    Page<Notice> findAllForAdmin(NoticeType type, Pageable pageable);
}
