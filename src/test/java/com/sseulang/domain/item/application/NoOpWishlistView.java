package com.sseulang.domain.item.application;

import com.sseulang.domain.item.domain.WishlistView;

import java.util.Collection;
import java.util.Set;

/**
 * 단위 테스트용 fake — 항상 빈 Set 반환. 즉 isWishlisted = 항상 false.
 *
 * <p>테스트가 wishlisted 상태를 검증하려면 이 클래스 대신 stub 또는 in-memory 구현으로 교체.</p>
 */
public class NoOpWishlistView implements WishlistView {

    @Override
    public Set<Long> findWishlistedItemIds(Long userId, Collection<Long> itemIds) {
        return Set.of();
    }
}
