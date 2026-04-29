package com.sseulang.domain.item.domain;

import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemTest {

    private static final long SELLER = 1L;
    private static final long CATEGORY = 7L;

    @Test
    @DisplayName("create 판매_정상_status=판매중, viewCount=0")
    void create_판매_정상() {
        Item item = Item.create(SELLER, CATEGORY, "title", "desc", 10_000L, null, null, TradeType.판매, "서울");

        assertThat(item.getSellerId()).isEqualTo(SELLER);
        assertThat(item.getCategoryId()).isEqualTo(CATEGORY);
        assertThat(item.getTitle()).isEqualTo("title");
        assertThat(item.getPrice()).isEqualTo(10_000L);
        assertThat(item.getDeposit()).isNull();
        assertThat(item.getRentalUnit()).isNull();
        assertThat(item.getTradeType()).isEqualTo(TradeType.판매);
        assertThat(item.getStatus()).isEqualTo(ItemStatus.판매중);
        assertThat(item.getViewCount()).isZero();
        assertThat(item.getImages()).isEmpty();
    }

    @Test
    @DisplayName("create 나눔_정상_price=0")
    void create_나눔_정상() {
        Item item = Item.create(SELLER, CATEGORY, "t", "d", 0L, null, null, TradeType.나눔, null);
        assertThat(item.getTradeType()).isEqualTo(TradeType.나눔);
        assertThat(item.getPrice()).isZero();
    }

    @Test
    @DisplayName("create 대여_정상_deposit/rentalUnit 박힘")
    void create_대여_정상() {
        Item item = Item.create(SELLER, CATEGORY, "t", "d", 5_000L, 50_000L, RentalUnit.일, TradeType.대여, null);
        assertThat(item.getDeposit()).isEqualTo(50_000L);
        assertThat(item.getRentalUnit()).isEqualTo(RentalUnit.일);
    }

    @Test
    @DisplayName("create 대여인데 deposit 누락_거부")
    void create_대여_deposit_누락_거부() {
        assertThatThrownBy(() ->
                Item.create(SELLER, CATEGORY, "t", "d", 1_000L, null, RentalUnit.일, TradeType.대여, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create 대여인데 rentalUnit 누락_거부")
    void create_대여_rentalUnit_누락_거부() {
        assertThatThrownBy(() ->
                Item.create(SELLER, CATEGORY, "t", "d", 1_000L, 10_000L, null, TradeType.대여, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create 판매인데 deposit 박으면_거부")
    void create_판매_deposit_박으면_거부() {
        assertThatThrownBy(() ->
                Item.create(SELLER, CATEGORY, "t", "d", 1_000L, 5_000L, null, TradeType.판매, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create 빈 title_거부")
    void create_빈_title_거부() {
        assertThatThrownBy(() ->
                Item.create(SELLER, CATEGORY, "", "d", 1_000L, null, null, TradeType.판매, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                Item.create(SELLER, CATEGORY, null, "d", 1_000L, null, null, TradeType.판매, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create title 200자 초과_거부")
    void create_title_길이초과_거부() {
        String tooLong = "가".repeat(201);
        assertThatThrownBy(() ->
                Item.create(SELLER, CATEGORY, tooLong, "d", 1_000L, null, null, TradeType.판매, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create 음수 price_거부")
    void create_음수_price_거부() {
        assertThatThrownBy(() ->
                Item.create(SELLER, CATEGORY, "t", "d", -1L, null, null, TradeType.판매, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create null sellerId_거부")
    void create_null_sellerId_거부() {
        assertThatThrownBy(() ->
                Item.create(null, CATEGORY, "t", "d", 1_000L, null, null, TradeType.판매, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("addImage 1~5장 정상")
    void addImage_5장_정상() {
        Item item = saleItem();
        for (int i = 1; i <= 5; i++) {
            item.addImage("https://img/" + i, i, i == 1);
        }
        assertThat(item.getImages()).hasSize(5);
        assertThat(item.getImages().get(0).isThumbnail()).isTrue();
    }

    @Test
    @DisplayName("addImage 6번째_ITEM_IMAGE_LIMIT_EXCEEDED")
    void addImage_6장_거부() {
        Item item = saleItem();
        for (int i = 1; i <= 5; i++) {
            item.addImage("https://img/" + i, i, false);
        }
        assertThatThrownBy(() -> item.addImage("https://img/6", 6, false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ITEM_IMAGE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("updateInfo 판매중_정상")
    void updateInfo_판매중_정상() {
        Item item = saleItem();
        item.updateInfo("new title", "new desc", 20_000L, null, null, "부산");

        assertThat(item.getTitle()).isEqualTo("new title");
        assertThat(item.getPrice()).isEqualTo(20_000L);
        assertThat(item.getRegion()).isEqualTo("부산");
    }

    @Test
    @DisplayName("updateInfo 거래완료_거부")
    void updateInfo_거래완료_거부() {
        Item item = saleItem();
        item.markAsDeleted();
        assertThatThrownBy(() ->
                item.updateInfo("x", "y", 1L, null, null, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("markAsHidden / restore 상태 전이")
    void hidden_restore() {
        Item item = saleItem();
        item.markAsHidden();
        assertThat(item.getStatus()).isEqualTo(ItemStatus.비공개);
        item.restore();
        assertThat(item.getStatus()).isEqualTo(ItemStatus.판매중);
    }

    @Test
    @DisplayName("markAsDeleted 후 restore_거부")
    void markAsDeleted_후_restore_거부() {
        Item item = saleItem();
        item.markAsDeleted();
        assertThatThrownBy(item::restore).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("incrementViewCount")
    void incrementViewCount() {
        Item item = saleItem();
        item.incrementViewCount();
        item.incrementViewCount();
        assertThat(item.getViewCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("isOwnedBy")
    void isOwnedBy() {
        Item item = saleItem();
        assertThat(item.isOwnedBy(SELLER)).isTrue();
        assertThat(item.isOwnedBy(SELLER + 1)).isFalse();
        assertThat(item.isOwnedBy(null)).isFalse();
    }

    @Test
    @DisplayName("addHashtag 정상_3개")
    void addHashtag_정상() {
        Item item = saleItem();
        item.addHashtag("아이폰");
        item.addHashtag("미개봉");
        item.addHashtag("정품");
        assertThat(item.getHashtags()).extracting("tag")
                .containsExactly("아이폰", "미개봉", "정품");
    }

    @Test
    @DisplayName("addHashtag 중복_무시")
    void addHashtag_중복_무시() {
        Item item = saleItem();
        item.addHashtag("아이폰");
        item.addHashtag("아이폰");
        item.addHashtag("  아이폰  ");  // 공백 정규화 후 동일
        assertThat(item.getHashtags()).hasSize(1);
    }

    @Test
    @DisplayName("addHashtag 빈 태그_거부")
    void addHashtag_빈_거부() {
        Item item = saleItem();
        assertThatThrownBy(() -> item.addHashtag(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item.addHashtag(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item.addHashtag("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("addHashtag 50자 초과_거부")
    void addHashtag_길이초과_거부() {
        Item item = saleItem();
        String tooLong = "가".repeat(51);
        assertThatThrownBy(() -> item.addHashtag(tooLong))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("clearHashtags")
    void clearHashtags() {
        Item item = saleItem();
        item.addHashtag("a");
        item.addHashtag("b");
        item.clearHashtags();
        assertThat(item.getHashtags()).isEmpty();
    }

    private static Item saleItem() {
        return Item.create(SELLER, CATEGORY, "t", "d", 10_000L, null, null, TradeType.판매, "서울");
    }
}
