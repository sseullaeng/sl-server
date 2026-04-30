package com.sseulang.domain.banner.application;

import com.sseulang.domain.banner.application.dto.BannerResult;
import com.sseulang.domain.banner.application.dto.BannerUpsertCommand;
import com.sseulang.domain.banner.domain.Banner;
import com.sseulang.domain.banner.domain.BannerRepository;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 배너 흐름 — 관리자 CRUD + activate/deactivate, 사용자는 활성 + 윈도우 안 배너만 sort_order 순으로.
 */
@Service
@Transactional(readOnly = true)
public class BannerApplicationService {

    private final BannerRepository bannerRepository;
    private final Clock clock;

    public BannerApplicationService(BannerRepository bannerRepository, Clock clock) {
        this.bannerRepository = bannerRepository;
        this.clock = clock;
    }

    @Transactional
    public Long create(Long adminId, BannerUpsertCommand cmd) {
        Banner b = Banner.create(
                adminId, cmd.title(), cmd.imageUrl(), cmd.linkUrl(),
                cmd.sortOrder(), cmd.startsAt(), cmd.endsAt()
        );
        return bannerRepository.save(b).getId();
    }

    @Transactional
    public void update(Long bannerId, BannerUpsertCommand cmd) {
        Banner b = findOrThrow(bannerId);
        b.update(cmd.title(), cmd.imageUrl(), cmd.linkUrl(),
                cmd.sortOrder(), cmd.startsAt(), cmd.endsAt());
    }

    @Transactional
    public void setActive(Long bannerId, boolean active) {
        Banner b = findOrThrow(bannerId);
        if (active) b.activate(); else b.deactivate();
    }

    @Transactional
    public void delete(Long bannerId) {
        if (bannerRepository.findById(bannerId).isEmpty()) {
            throw new BusinessException(ErrorCode.BANNER_NOT_FOUND);
        }
        bannerRepository.deleteById(bannerId);
    }

    public List<BannerResult> findVisible() {
        LocalDateTime now = LocalDateTime.now(clock);
        return bannerRepository.findVisibleSorted(now).stream()
                .map(BannerResult::from)
                .toList();
    }

    public Page<BannerResult> adminFindAll(Pageable pageable) {
        return bannerRepository.findAllForAdmin(pageable).map(BannerResult::from);
    }

    public BannerResult adminFindById(Long bannerId) {
        return BannerResult.from(findOrThrow(bannerId));
    }

    private Banner findOrThrow(Long id) {
        return bannerRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.BANNER_NOT_FOUND));
    }
}
