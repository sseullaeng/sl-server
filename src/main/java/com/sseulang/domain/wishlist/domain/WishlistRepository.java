package com.sseulang.domain.wishlist.domain;

import com.sseulang.domain.item.domain.Item;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WishlistRepository {

    boolean existsByUserIdAndItemId(Long userId, Long itemId);

    Wishlist save(Wishlist wishlist);

    /**
     * 멱등 삭제. 존재하지 않아도 예외 없이 0 건 처리. 영향받은 행 수를 반환 — 호출자가 후속 액션
     * (예: items.wishlist_count 감소)을 결정할 때 사용.
     */
    int deleteByUserIdAndItemId(Long userId, Long itemId);

    /**
     * 내 찜 목록 — 삭제된 Item 은 제외, Wishlist.createdAt DESC 로 페이징.
     * Cross-aggregate read (Wishlist join Item) — write 흐름은 분리되어 있어 read 만 허용.
     */
    Page<Item> findWishlistedItemsByUserId(Long userId, Pageable pageable);
}
