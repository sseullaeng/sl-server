package com.sseulang.domain.item.application;

import com.sseulang.domain.category.application.InMemoryFakeCategoryRepository;
import com.sseulang.domain.category.domain.Category;
import com.sseulang.domain.item.application.dto.ItemDetailResult;
import com.sseulang.domain.item.application.dto.ItemRegisterCommand;
import com.sseulang.domain.item.application.dto.ItemUpdateCommand;
import com.sseulang.domain.item.domain.ItemStatus;
import com.sseulang.domain.item.domain.RentalUnit;
import com.sseulang.domain.item.domain.TradeType;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemApplicationServiceTest {

    private static final Long SELLER = 100L;
    private static final Long OTHER = 200L;

    private InMemoryFakeItemRepository itemRepo;
    private InMemoryFakeCategoryRepository categoryRepo;
    private ItemApplicationService service;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        itemRepo = new InMemoryFakeItemRepository();
        categoryRepo = new InMemoryFakeCategoryRepository();
        service = new ItemApplicationService(itemRepo, categoryRepo);
        categoryId = categoryRepo.insert(Category.createRoot("디지털/가전", 1)).getId();
    }

    @Test
    @DisplayName("register 정상_id 발급되고 이미지+해시태그 add")
    void register_정상() {
        Long id = service.register(new ItemRegisterCommand(
                SELLER, categoryId, "title", "desc", 10_000L, null, null, TradeType.판매,
                "서울",
                List.of("https://img/1", "https://img/2"),
                List.of("아이폰", "미개봉")
        ));

        ItemDetailResult result = service.getById(id);
        assertThat(result.id()).isEqualTo(id);
        assertThat(result.sellerId()).isEqualTo(SELLER);
        assertThat(result.images()).hasSize(2);
        assertThat(result.images().get(0).thumbnail()).isTrue();
        assertThat(result.hashtags()).containsExactly("아이폰", "미개봉");
    }

    @Test
    @DisplayName("register 없는 카테고리_CATEGORY_NOT_FOUND")
    void register_없는_카테고리_거부() {
        assertThatThrownBy(() -> service.register(new ItemRegisterCommand(
                SELLER, 9999L, "t", "d", 1L, null, null, TradeType.판매, null, null, null
        )))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    @DisplayName("register null 카테고리_허용")
    void register_null_카테고리_허용() {
        Long id = service.register(new ItemRegisterCommand(
                SELLER, null, "t", "d", 1L, null, null, TradeType.판매, null, null, null
        ));
        assertThat(service.getById(id).categoryId()).isNull();
    }

    @Test
    @DisplayName("getById 정상_viewCount 1 증가")
    void getById_view_증가() {
        Long id = registerSimple();

        service.getById(id);
        ItemDetailResult result = service.getById(id);

        assertThat(result.viewCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("getById 없는 id_ITEM_NOT_FOUND")
    void getById_없음() {
        assertThatThrownBy(() -> service.getById(9999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("update 본인_정상")
    void update_본인() {
        Long id = registerSimple();

        service.update(id, SELLER, new ItemUpdateCommand(
                null, "new title", "new desc", 50_000L, null, null, "부산", null, null
        ));

        ItemDetailResult r = service.getById(id);
        assertThat(r.title()).isEqualTo("new title");
        assertThat(r.price()).isEqualTo(50_000L);
        assertThat(r.region()).isEqualTo("부산");
    }

    @Test
    @DisplayName("update 타인_ITEM_FORBIDDEN")
    void update_타인_거부() {
        Long id = registerSimple();

        assertThatThrownBy(() -> service.update(id, OTHER, new ItemUpdateCommand(
                null, "x", "y", 1L, null, null, null, null, null
        )))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ITEM_FORBIDDEN);
    }

    @Test
    @DisplayName("update imageUrls non-null_전체 교체")
    void update_이미지_전체교체() {
        Long id = service.register(new ItemRegisterCommand(
                SELLER, categoryId, "t", "d", 1L, null, null, TradeType.판매, null,
                List.of("https://old/1", "https://old/2"), null
        ));

        service.update(id, SELLER, new ItemUpdateCommand(
                null, "t", "d", 1L, null, null, null, List.of("https://new/1"), null
        ));

        ItemDetailResult r = service.getById(id);
        assertThat(r.images()).hasSize(1);
        assertThat(r.images().get(0).imageUrl()).isEqualTo("https://new/1");
    }

    @Test
    @DisplayName("update hashtags non-null_전체 교체")
    void update_해시태그_전체교체() {
        Long id = service.register(new ItemRegisterCommand(
                SELLER, categoryId, "t", "d", 1L, null, null, TradeType.판매, null,
                null, List.of("old1", "old2")
        ));

        service.update(id, SELLER, new ItemUpdateCommand(
                null, "t", "d", 1L, null, null, null, null, List.of("new1")
        ));

        ItemDetailResult r = service.getById(id);
        assertThat(r.hashtags()).containsExactly("new1");
    }

    @Test
    @DisplayName("update hashtags null_변경 없음")
    void update_해시태그_null_유지() {
        Long id = service.register(new ItemRegisterCommand(
                SELLER, categoryId, "t", "d", 1L, null, null, TradeType.판매, null,
                null, List.of("keep1", "keep2")
        ));

        service.update(id, SELLER, new ItemUpdateCommand(
                null, "t", "d", 1L, null, null, null, null, null
        ));

        ItemDetailResult r = service.getById(id);
        assertThat(r.hashtags()).containsExactly("keep1", "keep2");
    }

    @Test
    @DisplayName("delete 본인_status=삭제")
    void delete_본인() {
        Long id = registerSimple();

        service.delete(id, SELLER);

        assertThat(itemRepo.findById(id)).isPresent();
        assertThat(itemRepo.findById(id).get().getStatus()).isEqualTo(ItemStatus.삭제);
    }

    @Test
    @DisplayName("delete 타인_ITEM_FORBIDDEN")
    void delete_타인_거부() {
        Long id = registerSimple();

        assertThatThrownBy(() -> service.delete(id, OTHER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ITEM_FORBIDDEN);
    }

    @Test
    @DisplayName("update 대여_정상_deposit/rentalUnit 박힘")
    void update_대여() {
        Long id = service.register(new ItemRegisterCommand(
                SELLER, categoryId, "t", "d", 1L, 10_000L, RentalUnit.일, TradeType.대여, null,
                null, null
        ));

        service.update(id, SELLER, new ItemUpdateCommand(
                null, "t", "d", 1L, 20_000L, RentalUnit.주, null, null, null
        ));

        ItemDetailResult r = service.getById(id);
        assertThat(r.deposit()).isEqualTo(20_000L);
        assertThat(r.rentalUnit()).isEqualTo(RentalUnit.주);
    }

    private Long registerSimple() {
        return service.register(new ItemRegisterCommand(
                SELLER, categoryId, "t", "d", 1_000L, null, null, TradeType.판매, null, null, null
        ));
    }
}
