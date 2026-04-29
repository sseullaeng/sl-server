package com.sseulang.domain.wishlist.application;

import com.sseulang.domain.wishlist.domain.Wishlist;
import com.sseulang.domain.wishlist.domain.WishlistRepository;
import org.springframework.test.util.ReflectionTestUtils;

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
    public void deleteByUserIdAndItemId(Long userId, Long itemId) {
        store.remove(key(userId, itemId));
    }

    public int size() {
        return store.size();
    }
}
