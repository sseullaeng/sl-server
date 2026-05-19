package com.sseulang.domain.banner.infrastructure.persistence;

import com.sseulang.domain.banner.domain.Banner;
import com.sseulang.domain.banner.domain.BannerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class BannerRepositoryImpl implements BannerRepository {

    private final BannerJpaRepository jpa;

    public BannerRepositoryImpl(BannerJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Banner save(Banner banner) {
        return jpa.save(banner);
    }

    @Override
    public Optional<Banner> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public void deleteById(Long id) {
        jpa.deleteById(id);
    }

    @Override
    public List<Banner> findVisibleSorted(LocalDateTime now) {
        return jpa.findVisibleSorted(now);
    }

    @Override
    public Page<Banner> findAllForAdmin(Pageable pageable) {
        return jpa.findAllByOrderByIdDesc(pageable);
    }
}
