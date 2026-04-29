package com.sseulang.domain.item.application;

import com.sseulang.domain.category.application.CategoryApplicationService;
import com.sseulang.domain.item.application.dto.ItemDetailResult;
import com.sseulang.domain.item.application.dto.ItemForTransactionResult;
import com.sseulang.domain.item.application.dto.ItemRegisterCommand;
import com.sseulang.domain.item.application.dto.ItemSearchCriteria;
import com.sseulang.domain.item.application.dto.ItemSummaryResult;
import com.sseulang.domain.item.application.dto.ItemUpdateCommand;
import com.sseulang.domain.item.domain.Item;
import com.sseulang.domain.item.domain.ItemRepository;
import com.sseulang.domain.item.domain.ItemStatus;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ItemApplicationService {

    private final ItemRepository itemRepository;
    private final CategoryApplicationService categoryApplicationService;

    public ItemApplicationService(
            ItemRepository itemRepository,
            CategoryApplicationService categoryApplicationService
    ) {
        this.itemRepository = itemRepository;
        this.categoryApplicationService = categoryApplicationService;
    }

    @Transactional
    public Long register(ItemRegisterCommand cmd) {
        categoryApplicationService.requireExists(cmd.categoryId());
        Item item = Item.create(
                cmd.sellerId(), cmd.categoryId(),
                cmd.title(), cmd.description(),
                cmd.price(), cmd.deposit(), cmd.rentalUnit(), cmd.tradeType(),
                cmd.region()
        );
        applyImages(item, cmd.imageUrls());
        applyHashtags(item, cmd.hashtags());
        return itemRepository.save(item).getId();
    }

    @Transactional
    public ItemDetailResult getById(Long id) {
        Item item = findOrThrow(id);
        item.incrementViewCount();
        return ItemDetailResult.from(item);
    }

    public Page<ItemSummaryResult> search(ItemSearchCriteria criteria, Pageable pageable) {
        return itemRepository.search(criteria, pageable).map(ItemSummaryResult::from);
    }

    @Transactional
    public void update(Long id, Long requesterId, ItemUpdateCommand cmd) {
        Item item = findOwnedOrThrow(id, requesterId);

        item.updateInfo(
                cmd.title(), cmd.description(),
                cmd.price(), cmd.deposit(), cmd.rentalUnit(), cmd.region()
        );

        if (cmd.categoryId() != null) {
            categoryApplicationService.requireExists(cmd.categoryId());
            item.assignCategory(cmd.categoryId());
        }
        if (cmd.imageUrls() != null) {
            item.clearImages();
            applyImages(item, cmd.imageUrls());
        }
        if (cmd.hashtags() != null) {
            item.clearHashtags();
            applyHashtags(item, cmd.hashtags());
        }
    }

    @Transactional
    public void delete(Long id, Long requesterId) {
        Item item = findOwnedOrThrow(id, requesterId);
        item.markAsDeleted();
    }

    /**
     * 다른 도메인 ApplicationService 가 "활성 Item 존재"만 검증할 때 사용 — Wishlist 등.
     * status=삭제 는 ITEM_NOT_FOUND. CLAUDE.md §3.3 의 다른 도메인 Repository 직접 호출 금지 룰
     * 정합 — 외부는 본 메서드를 통해서만 Item 검증.
     */
    public void requireActiveItem(Long id) {
        Item item = findOrThrow(id);
        if (item.getStatus() == ItemStatus.삭제) {
            throw new BusinessException(ErrorCode.ITEM_NOT_FOUND);
        }
    }

    /**
     * Chat / 거래 시작 등 다른 도메인이 Item 의 seller 를 알아야 할 때.
     * 삭제: ITEM_NOT_FOUND, 비공개: ITEM_INVALID_STATE. 판매중/예약/거래완료 는 채팅 가능.
     */
    public Long findSellerOfActiveItem(Long id) {
        Item item = findOrThrow(id);
        if (item.getStatus() == ItemStatus.삭제) {
            throw new BusinessException(ErrorCode.ITEM_NOT_FOUND);
        }
        if (item.getStatus() == ItemStatus.비공개) {
            throw new BusinessException(ErrorCode.ITEM_INVALID_STATE);
        }
        return item.getSellerId();
    }

    /**
     * 거래 도메인이 거래 생성 시 호출. 비관적 락(PESSIMISTIC_WRITE)으로 Item 행을 잠근 뒤 활성(판매중) 검증.
     * 가이드 §5.2 — reserve 와 create 동시 시 락 직렬화로 "예약 직후 새 채팅중 거래 저장" 회귀 차단.
     * Codex 게이트 1 Warning 보강.
     */
    @Transactional
    public ItemForTransactionResult findActiveForTransaction(Long itemId) {
        Item item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
        if (item.getStatus() != ItemStatus.판매중) {
            throw new BusinessException(ErrorCode.ITEM_INVALID_STATE);
        }
        return new ItemForTransactionResult(
                item.getId(), item.getSellerId(), item.getTradeType(), item.getPrice(), item.getDeposit()
        );
    }

    /**
     * 거래 도메인이 reserve 시 호출. 비관적 락(PESSIMISTIC_WRITE)으로 Item 행을 잠그고 markAsReserved 호출.
     * 가이드 §5.2 동시 거래 차단의 핵심 — 같은 트랜잭션 안에서 호출되면 락 유지 + 도메인 invariant 검증.
     */
    @Transactional
    public void markItemAsReserved(Long itemId) {
        Item item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
        item.markAsReserved();
    }

    /** 거래 도메인이 complete 시 호출. 비관적 락 + 예약 → 거래완료. */
    @Transactional
    public void markItemAsSold(Long itemId) {
        Item item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
        item.markAsSold();
    }

    /** 거래 도메인이 cancel 시 호출 (예약 상태였던 거래만). 예약 → 판매중 복원. */
    @Transactional
    public void restoreItemFromReserved(Long itemId) {
        Item item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
        item.restoreFromReserved();
    }

    /**
     * {@code wishlist_count} 원자 증가. Wishlist 도메인이 찜 추가 성공 직후 호출.
     *
     * <p><b>Stale 주의(bulk update)</b>: JPA bulk update 라 persistence context 가 자동 동기화되지
     * 않는다. 같은 트랜잭션 안에서 후속으로 동일 {@code Item} 을 다시 읽을 경우 stale 값을 받을 수
     * 있으므로 그때는 {@code EntityManager.refresh} 또는 {@code flush+clear} 필요. 현재 wishlist
     * add/remove 흐름은 호출 직후 read 가 없어 안전.</p>
     */
    @Transactional
    public void incrementWishlistCount(Long itemId) {
        itemRepository.incrementWishlistCount(itemId);
    }

    /**
     * {@code wishlist_count} 원자 감소. 음수 방지는 Repository 쪽 SQL 가드.
     * Stale 주의는 {@link #incrementWishlistCount} 와 동일.
     */
    @Transactional
    public void decrementWishlistCount(Long itemId) {
        itemRepository.decrementWishlistCount(itemId);
    }

    private Item findOrThrow(Long id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
    }

    private Item findOwnedOrThrow(Long id, Long requesterId) {
        Item item = findOrThrow(id);
        if (!item.isOwnedBy(requesterId)) {
            throw new BusinessException(ErrorCode.ITEM_FORBIDDEN);
        }
        return item;
    }

    private static void applyImages(Item item, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return;
        }
        int order = 0;
        for (String url : imageUrls) {
            order++;
            item.addImage(url, order, order == 1);
        }
    }

    private static void applyHashtags(Item item, List<String> hashtags) {
        if (hashtags == null || hashtags.isEmpty()) {
            return;
        }
        for (String tag : hashtags) {
            item.addHashtag(tag);
        }
    }
}
