package com.sseulang.domain.wishlist.application;

import com.sseulang.domain.item.domain.Item;
import com.sseulang.domain.item.domain.ItemRepository;
import com.sseulang.domain.item.domain.ItemStatus;
import com.sseulang.domain.wishlist.domain.Wishlist;
import com.sseulang.domain.wishlist.domain.WishlistRepository;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WishlistApplicationService {

    private final WishlistRepository wishlistRepository;
    private final ItemRepository itemRepository;

    public WishlistApplicationService(
            WishlistRepository wishlistRepository,
            ItemRepository itemRepository
    ) {
        this.wishlistRepository = wishlistRepository;
        this.itemRepository = itemRepository;
    }

    /**
     * 찜 추가. 이미 있으면 멱등하게 무시. 삭제된 물품은 거부.
     * UNIQUE(user_id, item_id) race 는 catch 후 무시.
     */
    @Transactional
    public void add(Long userId, Long itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
        if (item.getStatus() == ItemStatus.삭제) {
            throw new BusinessException(ErrorCode.ITEM_NOT_FOUND);
        }
        if (wishlistRepository.existsByUserIdAndItemId(userId, itemId)) {
            return;
        }
        try {
            wishlistRepository.save(Wishlist.create(userId, itemId));
        } catch (DataIntegrityViolationException race) {
            // UNIQUE 충돌 race — 동일 userId+itemId 가 동시에 박힌 경우. 후속 호출도 결과 동일하므로 무시.
        }
    }

    /** 멱등 삭제. 없는 항목은 그냥 통과. */
    @Transactional
    public void remove(Long userId, Long itemId) {
        wishlistRepository.deleteByUserIdAndItemId(userId, itemId);
    }
}
