package com.sseulang.domain.wishlist.infrastructure.persistence;

import com.sseulang.domain.item.domain.WishlistView;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;

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
