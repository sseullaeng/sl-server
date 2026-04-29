package com.sseulang.domain.wishlist.domain;

public interface WishlistRepository {

    boolean existsByUserIdAndItemId(Long userId, Long itemId);

    Wishlist save(Wishlist wishlist);

    /** 멱등 삭제. 존재하지 않아도 예외 없이 0 건 처리. */
    void deleteByUserIdAndItemId(Long userId, Long itemId);
}
