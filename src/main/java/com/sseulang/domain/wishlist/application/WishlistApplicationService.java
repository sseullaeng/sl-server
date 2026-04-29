package com.sseulang.domain.wishlist.application;

import com.sseulang.domain.item.application.ItemApplicationService;
import com.sseulang.domain.wishlist.domain.Wishlist;
import com.sseulang.domain.wishlist.domain.WishlistRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WishlistApplicationService {

    /**
     * V1 스키마({@code uk_wishlists_user_item}) 의 UNIQUE 제약 이름. 동시 add race 만 멱등 처리하고
     * 그 외 무결성 위반(FK / 다른 제약)은 그대로 던져 시스템 에러로 노출한다 — Codex 게이트 2 보정.
     */
    private static final String UNIQUE_USER_ITEM = "uk_wishlists_user_item";

    private final WishlistRepository wishlistRepository;
    private final ItemApplicationService itemApplicationService;

    public WishlistApplicationService(
            WishlistRepository wishlistRepository,
            ItemApplicationService itemApplicationService
    ) {
        this.wishlistRepository = wishlistRepository;
        this.itemApplicationService = itemApplicationService;
    }

    /**
     * 찜 추가. Item 존재 검증은 {@link ItemApplicationService#requireActiveItem} 위임 (CLAUDE.md
     * §3.3 — 다른 도메인 Repository 직접 호출 금지). 이미 있으면 멱등하게 무시.
     * UNIQUE(user_id, item_id) race 는 catch 후 무시 — 그 외 무결성 위반은 재던지기.
     */
    @Transactional
    public void add(Long userId, Long itemId) {
        itemApplicationService.requireActiveItem(itemId);
        if (wishlistRepository.existsByUserIdAndItemId(userId, itemId)) {
            return;
        }
        try {
            wishlistRepository.save(Wishlist.create(userId, itemId));
            itemApplicationService.incrementWishlistCount(itemId);
        } catch (DataIntegrityViolationException violation) {
            if (isUniqueUserItemConflict(violation)) {
                return;
            }
            throw violation;
        }
    }

    /**
     * 멱등 삭제. 실제 삭제된 row 가 1 건일 때만 wishlist_count 감소 — 동시 remove 시 underflow 방지.
     */
    @Transactional
    public void remove(Long userId, Long itemId) {
        int deleted = wishlistRepository.deleteByUserIdAndItemId(userId, itemId);
        if (deleted > 0) {
            itemApplicationService.decrementWishlistCount(itemId);
        }
    }

    private static boolean isUniqueUserItemConflict(DataIntegrityViolationException violation) {
        Throwable cause = violation.getCause();
        return cause instanceof ConstraintViolationException cve
                && UNIQUE_USER_ITEM.equalsIgnoreCase(cve.getConstraintName());
    }
}
