package com.sseulang.domain.wishlist.infrastructure.persistence;

import com.sseulang.domain.item.domain.WishlistView;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;

/**
 * {@link WishlistView} 어댑터. {@code item} 도메인이 viewer 의 찜한 itemId 집합을 알아야 할 때
 * (목록·검색·내물품 응답의 isWishlisted 매핑) 단일 SELECT 로 응답.
 *
 * <p>userId 가 null 또는 itemIds 가 비면 즉시 {@code Set.of()} — DB 호출 안 함.</p>
 */
@Component
class WishlistViewAdapter implements WishlistView {

    private final WishlistJpaRepository jpa;

    WishlistViewAdapter(WishlistJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Set<Long> findWishlistedItemIds(Long userId, Collection<Long> itemIds) {
        if (userId == null || itemIds == null || itemIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(jpa.findItemIdsByUserIdAndItemIds(userId, itemIds));
    }
}
