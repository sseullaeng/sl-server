package com.sseulang.domain.wishlist.domain;

public interface WishlistRepository {

    boolean existsByUserIdAndItemId(Long userId, Long itemId);

    Wishlist save(Wishlist wishlist);

    /**
     * 멱등 삭제. 존재하지 않아도 예외 없이 0 건 처리. 영향받은 행 수를 반환 — 호출자가 후속 액션
     * (예: items.wishlist_count 감소)을 결정할 때 사용.
     */
    int deleteByUserIdAndItemId(Long userId, Long itemId);
}
