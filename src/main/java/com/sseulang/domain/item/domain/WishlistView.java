package com.sseulang.domain.item.domain;

import java.util.Collection;
import java.util.Set;

/**
 * Item 도메인이 viewer 의 찜 여부를 알아야 할 때 쓰는 read-only 포트.
 * 구현은 {@code wishlist.infrastructure} 의 어댑터.
 *
 * <p>CLAUDE.md §3.3 — 다른 도메인 Repository 직접 호출 금지. 여기서는 cross-aggregate read 용
 * 포트만 정의하고 wishlist 인프라가 구현 (anti-corruption + 단방향 의존).</p>
 */
public interface WishlistView {

    /**
     * 주어진 itemId 들 중 userId 가 찜한 것만 반환. userId / itemIds 가 비어있으면 {@code Set.of()}.
     */
    Set<Long> findWishlistedItemIds(Long userId, Collection<Long> itemIds);
}
