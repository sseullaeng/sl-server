package com.sseulang.domain.banner.application;

import com.sseulang.domain.banner.domain.Banner;
import com.sseulang.domain.banner.domain.BannerRepository;
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

class InMemoryFakeBannerRepository implements BannerRepository {

    private final Map<Long, Banner> store = new HashMap<>();
    private long sequence = 0;

    @Override
    public Banner save(Banner banner) {
        if (banner.getId() == null) {
            ReflectionTestUtils.setField(banner, "id", ++sequence);
        }
        store.put(banner.getId(), banner);
        return banner;
    }

    @Override
    public Optional<Banner> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }

    @Override
    public List<Banner> findVisibleSorted(LocalDateTime now) {
        return store.values().stream()
                .filter(b -> b.isVisibleAt(now))
                .sorted(Comparator.comparingInt(Banner::getSortOrder)
                        .thenComparing(Banner::getId))
                .toList();
    }

    @Override
    public Page<Banner> findAllForAdmin(Pageable pageable) {
        List<Banner> all = store.values().stream()
                .sorted(Comparator.comparing(Banner::getId).reversed())
                .toList();
        return new PageImpl<>(all, pageable, all.size());
    }

    int size() {
        return store.size();
    }
}
