package com.sseulang.domain.wishlist.application;

import com.sseulang.domain.item.application.ItemApplicationService;
import com.sseulang.domain.item.application.dto.ItemSummaryResult;
import com.sseulang.domain.wishlist.application.dto.WishlistToggleResult;
import com.sseulang.domain.wishlist.domain.Wishlist;
import com.sseulang.domain.wishlist.domain.WishlistRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WishlistApplicationService {

    

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

    

    @Transactional
    public WishlistToggleResult add(Long userId, Long itemId) {
        itemApplicationService.requireActiveItem(itemId);
        if (wishlistRepository.existsByUserIdAndItemId(userId, itemId)) {
            return new WishlistToggleResult(true, itemApplicationService.getWishlistCount(itemId));
        }
        try {
            wishlistRepository.save(Wishlist.create(userId, itemId));
            itemApplicationService.incrementWishlistCount(itemId);
        } catch (DataIntegrityViolationException violation) {
            if (!isUniqueUserItemConflict(violation)) {
                throw violation;
            }
            
        }
        return new WishlistToggleResult(true, itemApplicationService.getWishlistCount(itemId));
    }

    

    public Page<ItemSummaryResult> listMyWishlistedItems(Long userId, Pageable pageable) {
        return wishlistRepository.findWishlistedItemsByUserId(userId, pageable)
                .map(item -> ItemSummaryResult.from(item, true));
    }

    

    @Transactional
    public WishlistToggleResult remove(Long userId, Long itemId) {
        int deleted = wishlistRepository.deleteByUserIdAndItemId(userId, itemId);
        if (deleted > 0) {
            itemApplicationService.decrementWishlistCount(itemId);
        }
        return new WishlistToggleResult(false, itemApplicationService.getWishlistCount(itemId));
    }

    

    private static boolean isUniqueUserItemConflict(DataIntegrityViolationException violation) {
        Throwable cause = violation;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException cve
                    && UNIQUE_USER_ITEM.equalsIgnoreCase(cve.getConstraintName())) {
                return true;
            }
            Throwable next = cause.getCause();
            if (next == cause) {
                return false; 
            }
            cause = next;
        }
        return false;
    }
}
