package com.sseulang.domain.wishlist.infrastructure.persistence;

import com.sseulang.domain.wishlist.domain.Wishlist;
import com.sseulang.domain.wishlist.domain.WishlistRepository;
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
}
