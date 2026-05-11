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

    
    List<Banner> findVisibleSorted(LocalDateTime now);

    
    Page<Banner> findAllForAdmin(Pageable pageable);
}
