package com.sseulang.domain.wishlist.application;

import com.sseulang.domain.category.application.CategoryApplicationService;
import com.sseulang.domain.category.application.InMemoryFakeCategoryRepository;
import com.sseulang.domain.item.application.InMemoryFakeItemRepository;
import com.sseulang.domain.item.application.ItemApplicationService;
import com.sseulang.domain.item.domain.Item;
import com.sseulang.domain.item.domain.TradeType;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WishlistApplicationServiceTest {

    private static final Long USER = 1L;

    private InMemoryFakeWishlistRepository wishRepo;
    private InMemoryFakeItemRepository itemRepo;
    private WishlistApplicationService service;
    private Long itemId;

    @BeforeEach
    void setUp() {
        wishRepo = new InMemoryFakeWishlistRepository();
        itemRepo = new InMemoryFakeItemRepository();
        CategoryApplicationService catSvc = new CategoryApplicationService(new InMemoryFakeCategoryRepository());
        ItemApplicationService itemSvc = new ItemApplicationService(itemRepo, catSvc, org.mockito.Mockito.mock(com.sseulang.domain.user.application.UserApplicationService.class), new com.sseulang.domain.file.application.NoOpPresignedUrlGenerator(), new com.sseulang.domain.item.application.NoOpWishlistView(), new com.sseulang.domain.item.application.NoOpItemReportView());
        service = new WishlistApplicationService(wishRepo, itemSvc);

        Item item = itemRepo.save(Item.create(
                999L, null, "t", "d", 1L, null, null, TradeType.판매, null
        ));
        itemId = item.getId();
    }

    @Test
    @DisplayName("add 정상_저장 + wishlistCount 증가")
    void add_정상() {
        service.add(USER, itemId);

        assertThat(wishRepo.existsByUserIdAndItemId(USER, itemId)).isTrue();
        assertThat(wishRepo.size()).isEqualTo(1);
        assertThat(itemRepo.findById(itemId).orElseThrow().getWishlistCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("add 중복_idempotent + 카운터 1 유지")
    void add_중복() {
        service.add(USER, itemId);
        service.add(USER, itemId);
        service.add(USER, itemId);

        assertThat(wishRepo.size()).isEqualTo(1);
        assertThat(itemRepo.findById(itemId).orElseThrow().getWishlistCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("add 없는 item_ITEM_NOT_FOUND")
    void add_없는_item() {
        assertThatThrownBy(() -> service.add(USER, 9999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("add 삭제된 item_ITEM_NOT_FOUND")
    void add_삭제된_item() {
        Item item = itemRepo.findById(itemId).orElseThrow();
        item.markAsDeleted();
        itemRepo.save(item);

        assertThatThrownBy(() -> service.add(USER, itemId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("remove 정상_카운터 감소")
    void remove_정상() {
        service.add(USER, itemId);
        service.remove(USER, itemId);

        assertThat(wishRepo.existsByUserIdAndItemId(USER, itemId)).isFalse();
        assertThat(itemRepo.findById(itemId).orElseThrow().getWishlistCount()).isZero();
    }

    @Test
    @DisplayName("remove 없는 항목_idempotent + 카운터 변동 없음")
    void remove_없는_항목() {
        service.remove(USER, itemId);

        assertThat(wishRepo.size()).isZero();
        assertThat(itemRepo.findById(itemId).orElseThrow().getWishlistCount()).isZero();
    }

    @Test
    @DisplayName("add → remove 반복_카운터 음수 안 됨")
    void 반복_카운터_정합() {
        service.add(USER, itemId);
        service.remove(USER, itemId);
        service.remove(USER, itemId);  // 한 번 더 — 멱등

        assertThat(itemRepo.findById(itemId).orElseThrow().getWishlistCount()).isZero();
    }
}
