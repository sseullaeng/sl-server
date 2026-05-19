package com.sseulang.domain.wishlist.domain;

import com.sseulang.domain.item.domain.Item;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WishlistRepository {

    boolean existsByUserIdAndItemId(Long userId, Long itemId);

    Wishlist save(Wishlist wishlist);

    

    int deleteByUserIdAndItemId(Long userId, Long itemId);

    

    Page<Item> findWishlistedItemsByUserId(Long userId, Pageable pageable);
}
