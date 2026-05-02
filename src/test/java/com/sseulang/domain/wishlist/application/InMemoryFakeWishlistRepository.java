package com.sseulang.domain.wishlist.application;

import com.sseulang.domain.item.domain.Item;
import com.sseulang.domain.wishlist.domain.Wishlist;
import com.sseulang.domain.wishlist.domain.WishlistRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class InMemoryFakeWishlistRepository implements WishlistRepository {

    /** key = userId|itemId */
    private final Map<String, Wishlist> store = new HashMap<>();
    private long sequence = 0;

    private static String key(Long userId, Long itemId) {
        return userId + "|" + itemId;
    }

    @Override
    public boolean existsByUserIdAndItemId(Long userId, Long itemId) {
        return store.containsKey(key(userId, itemId));
    }

    @Override
    public Wishlist save(Wishlist wishlist) {
        if (wishlist.getId() == null) {
            ReflectionTestUtils.setField(wishlist, "id", ++sequence);
        }
        store.put(key(wishlist.getUserId(), wishlist.getItemId()), wishlist);
        return wishlist;
    }

    @Override
    public int deleteByUserIdAndItemId(Long userId, Long itemId) {
        return store.remove(key(userId, itemId)) != null ? 1 : 0;
    }

    @Override
    public Page<Item> findWishlistedItemsByUserId(Long userId, Pageable pageable) {
        // 단위 테스트용 — Item 본체 join 은 IT 에서 검증. 빈 페이지 반환.
        return new PageImpl<>(Collections.emptyList(), pageable, 0);
    }

    public int size() {
        return store.size();
    }
}
