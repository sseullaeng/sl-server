package com.sseulang.domain.banner.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BannerRepository {

    Banner save(Banner banner);

    Optional<Banner> findById(Long id);

    void deleteById(Long id);

    /** 사용자 — 활성 + 윈도우 안 배너, sort_order ASC 정렬. */
    List<Banner> findVisibleSorted(LocalDateTime now);

    /** 관리자 — 전체 페이징. created_at DESC. */
    Page<Banner> findAllForAdmin(Pageable pageable);
}
