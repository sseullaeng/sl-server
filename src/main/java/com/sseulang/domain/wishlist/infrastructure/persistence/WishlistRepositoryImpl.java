package com.sseulang.domain.wishlist.infrastructure.persistence;

import com.sseulang.domain.item.domain.Item;
import com.sseulang.domain.wishlist.domain.Wishlist;
import com.sseulang.domain.wishlist.domain.WishlistRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public class WishlistRepositoryImpl implements WishlistRepository {

    private final WishlistJpaRepository jpa;

    public WishlistRepositoryImpl(WishlistJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public boolean existsByUserIdAndItemId(Long userId, Long itemId) {
        return jpa.existsByUserIdAndItemId(userId, itemId);
    }

    @Override
    public Wishlist save(Wishlist wishlist) {
        return jpa.save(wishlist);
    }

    @Override
    public int deleteByUserIdAndItemId(Long userId, Long itemId) {
        return jpa.deleteByUserAndItem(userId, itemId);
    }

    @Override
    public Page<Item> findWishlistedItemsByUserId(Long userId, Pageable pageable) {
        return jpa.findWishlistedItems(userId, pageable);
    }
}
