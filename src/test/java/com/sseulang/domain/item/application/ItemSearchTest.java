package com.sseulang.domain.item.application;

import com.sseulang.domain.category.application.InMemoryFakeCategoryRepository;
import com.sseulang.domain.category.domain.Category;
import com.sseulang.domain.item.application.dto.ItemRegisterCommand;
import com.sseulang.domain.item.application.dto.ItemSearchCriteria;
import com.sseulang.domain.item.application.dto.ItemSummaryResult;
import com.sseulang.domain.item.domain.RentalUnit;
import com.sseulang.domain.item.domain.TradeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 검색·필터 단위 테스트. {@link InMemoryFakeItemRepository#search} 는 prod QueryDSL 구현과
 * 동일한 필터 의미를 가져야 한다 — 본 테스트는 그 계약을 검증.
 */
class ItemSearchTest {

    private static final Long SELLER = 100L;

    private InMemoryFakeItemRepository itemRepo;
    private ItemApplicationService service;
    private Long catA;
    private Long catB;

    @BeforeEach
    void setUp() {
        itemRepo = new InMemoryFakeItemRepository();
        InMemoryFakeCategoryRepository categoryRepo = new InMemoryFakeCategoryRepository();
        com.sseulang.domain.category.application.CategoryApplicationService catSvc =
                new com.sseulang.domain.category.application.CategoryApplicationService(categoryRepo);
        service = new ItemApplicationService(itemRepo, catSvc, org.mockito.Mockito.mock(com.sseulang.domain.user.application.UserApplicationService.class), new com.sseulang.domain.file.application.NoOpPresignedUrlGenerator(), new com.sseulang.domain.item.application.NoOpWishlistView());
        catA = categoryRepo.insert(Category.createRoot("A", 1)).getId();
        catB = categoryRepo.insert(Category.createRoot("B", 2)).getId();
    }

    @Test
    @DisplayName("search 빈 criteria_삭제 제외 전체")
    void search_빈_criteria() {
        register("아이폰", "박스 미개봉", catA, TradeType.판매, 100_000L, null);
        register("갤럭시", "S급", catB, TradeType.판매, 200_000L, null);

        Page<ItemSummaryResult> result = service.search(ItemSearchCriteria.empty(), PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("search q=title 부분일치")
    void search_q_title() {
        register("아이폰 14 Pro", "박스 미개봉", catA, TradeType.판매, 100_000L, null);
        register("갤럭시 S24", "S급", catB, TradeType.판매, 200_000L, null);

        Page<ItemSummaryResult> result = service.search(
                new com.sseulang.domain.item.application.dto.ItemSearchCriteria("아이폰", null, null, null, null, null, null, com.sseulang.domain.item.application.dto.ItemSort.LATEST),
                PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(ItemSummaryResult::title)
                .containsExactly("아이폰 14 Pro");
    }

    @Test
    @DisplayName("search q=description 부분일치")
    void search_q_description() {
        register("물건1", "정품 풀박스", catA, TradeType.판매, 100_000L, null);
        register("물건2", "사용감 있음", catA, TradeType.판매, 200_000L, null);

        Page<ItemSummaryResult> result = service.search(
                new com.sseulang.domain.item.application.dto.ItemSearchCriteria("정품", null, null, null, null, null, null, com.sseulang.domain.item.application.dto.ItemSort.LATEST),
                PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("물건1");
    }

    @Test
    @DisplayName("search categoryId 필터")
    void search_categoryId() {
        register("a1", "d", catA, TradeType.판매, 100L, null);
        register("a2", "d", catA, TradeType.판매, 200L, null);
        register("b1", "d", catB, TradeType.판매, 300L, null);

        Page<ItemSummaryResult> result = service.search(
                new com.sseulang.domain.item.application.dto.ItemSearchCriteria(null, catA, null, null, null, null, null, com.sseulang.domain.item.application.dto.ItemSort.LATEST),
                PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("search tradeType 필터")
    void search_tradeType() {
        register("판매물", "d", catA, TradeType.판매, 100L, null);
        register("나눔물", "d", catA, TradeType.나눔, 0L, null);
        register("대여물", "d", catA, TradeType.대여, 100L, RentalUnit.일);

        Page<ItemSummaryResult> result = service.search(
                new com.sseulang.domain.item.application.dto.ItemSearchCriteria(null, null, TradeType.나눔, null, null, null, null, com.sseulang.domain.item.application.dto.ItemSort.LATEST),
                PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("나눔물");
    }

    @Test
    @DisplayName("search 가격 범위 필터")
    void search_price_range() {
        register("싼것", "d", catA, TradeType.판매, 1_000L, null);
        register("중간", "d", catA, TradeType.판매, 50_000L, null);
        register("비싼것", "d", catA, TradeType.판매, 1_000_000L, null);

        Page<ItemSummaryResult> result = service.search(
                new com.sseulang.domain.item.application.dto.ItemSearchCriteria(null, null, null, 10_000L, 100_000L, null, null, com.sseulang.domain.item.application.dto.ItemSort.LATEST),
                PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(ItemSummaryResult::title)
                .containsExactly("중간");
    }

    @Test
    @DisplayName("search tag 필터")
    void search_tag() {
        Long id1 = service.register(new ItemRegisterCommand(
                SELLER, catA, "i1", "d", 1L, null, null, TradeType.판매, null,
                null, List.of("아이폰", "미개봉")
        ));
        Long id2 = service.register(new ItemRegisterCommand(
                SELLER, catA, "i2", "d", 1L, null, null, TradeType.판매, null,
                null, List.of("갤럭시")
        ));

        Page<ItemSummaryResult> result = service.search(
                new com.sseulang.domain.item.application.dto.ItemSearchCriteria(null, null, null, null, null, "아이폰", null, com.sseulang.domain.item.application.dto.ItemSort.LATEST),
                PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(ItemSummaryResult::id)
                .containsExactly(id1);
        assertThat(id2).isNotEqualTo(id1);
    }

    @Test
    @DisplayName("search 삭제 상태 제외")
    void search_삭제_제외() {
        Long alive = registerSimple();
        Long willDelete = registerSimple();
        service.delete(willDelete, SELLER);

        Page<ItemSummaryResult> result = service.search(ItemSearchCriteria.empty(), PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(ItemSummaryResult::id).containsExactly(alive);
    }

    @Test
    @DisplayName("search 페이징")
    void search_페이징() {
        for (int i = 0; i < 25; i++) {
            registerSimple();
        }
        Page<ItemSummaryResult> page0 = service.search(ItemSearchCriteria.empty(), PageRequest.of(0, 10));
        Page<ItemSummaryResult> page2 = service.search(ItemSearchCriteria.empty(), PageRequest.of(2, 10));

        assertThat(page0.getContent()).hasSize(10);
        assertThat(page0.getTotalElements()).isEqualTo(25);
        assertThat(page0.getTotalPages()).isEqualTo(3);
        assertThat(page2.getContent()).hasSize(5);
    }

    @Test
    @DisplayName("search 결합 — categoryId + tradeType + price")
    void search_결합() {
        register("a1", "d", catA, TradeType.판매, 50_000L, null);
        register("a2", "d", catA, TradeType.대여, 50_000L, RentalUnit.일);
        register("b1", "d", catB, TradeType.판매, 50_000L, null);
        register("a3", "d", catA, TradeType.판매, 5_000_000L, null);

        Page<ItemSummaryResult> result = service.search(
                new com.sseulang.domain.item.application.dto.ItemSearchCriteria(null, catA, TradeType.판매, 0L, 100_000L, null, null, com.sseulang.domain.item.application.dto.ItemSort.LATEST),
                PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(ItemSummaryResult::title).containsExactly("a1");
    }

    private Long register(String title, String desc, Long categoryId, TradeType type, long price, RentalUnit unit) {
        Long deposit = type.requiresDeposit() ? 10_000L : null;
        return service.register(new ItemRegisterCommand(
                SELLER, categoryId, title, desc, price, deposit, unit, type, null, null, null
        ));
    }

    private Long registerSimple() {
        return service.register(new ItemRegisterCommand(
                SELLER, catA, "t" + System.nanoTime(), "d", 1L, null, null, TradeType.판매, null, null, null
        ));
    }
}
